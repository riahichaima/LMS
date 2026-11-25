package com.course.lms.controller;

import com.course.lms.model.Course;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.security.jwt.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vidéo d'un cours payant lue en streaming sur un vrai serveur : lien signé remis seulement à qui a le droit,
 * puis lecture par morceaux (206) sans jeton ; lien modifié ou détourné refusé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StreamingIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserDetailsService userDetails;

    private final HttpClient http = HttpClient.newHttpClient();
    private User teacher, outsider;
    private long resource;
    private Path video;
    private final byte[] content = new byte[100_000];

    @BeforeEach
    void data() throws Exception {
        teacher = user("TEACHER");
        outsider = user("STUDENT");
        Course c = new Course();
        c.setTitle("Cours vidéo payant");
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        c.setPricingType("PAID");
        c.setPrice(new BigDecimal("20.00"));
        long course = courses.save(c).getId();
        long chapter = insert("INSERT INTO chapters (title, course_id, created_at) VALUES ('Chapitre', ?, NOW())", course);
        long lesson = insert("INSERT INTO lessons (title, chapter_id, created_at) VALUES ('Leçon', ?, NOW())", chapter);
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 251);
        Path dir = Path.of("uploads", "videos");
        Files.createDirectories(dir);
        video = Files.write(dir.resolve("stream-" + UUID.randomUUID() + ".mp4"), content).toAbsolutePath();
        resource = insert("INSERT INTO lesson_resources (title, resource_type, file_path, mime_type, lesson_id, created_at) "
                + "VALUES ('Vidéo', 'VIDEO', ?, 'video/mp4', ?, NOW())", video.toString(), lesson);
    }

    @AfterEach
    void cleanUp() throws Exception {
        Files.deleteIfExists(video);
    }

    @Test
    void theVideoStartsWithAPartOfTheFileWithoutToken() throws Exception {
        String url = streamUrl(teacher);
        HttpResponse<byte[]> part = http.send(HttpRequest.newBuilder(URI.create(base() + url)).header("Range", "bytes=0-9").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(part.statusCode()).isEqualTo(206);
        assertThat(part.headers().firstValue("Content-Range")).hasValue("bytes 0-9/" + content.length);
        assertThat(part.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("video/mp4"));
        assertThat(part.body()).containsExactly(java.util.Arrays.copyOf(content, 10));

        HttpResponse<byte[]> middle = http.send(HttpRequest.newBuilder(URI.create(base() + url)).header("Range", "bytes=50000-50003").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(middle.statusCode()).isEqualTo(206);
        assertThat(middle.body()).containsExactly(java.util.Arrays.copyOfRange(content, 50000, 50004));

        HttpResponse<byte[]> whole = http.send(HttpRequest.newBuilder(URI.create(base() + url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(whole.statusCode()).isEqualTo(200);
        assertThat(whole.body()).hasSize(content.length);

        HttpResponse<Void> save = http.send(HttpRequest.newBuilder(URI.create(base() + url + "&download=true")).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(save.headers().firstValue("Content-Disposition")).hasValueSatisfying(v -> assertThat(v).startsWith("attachment"));
    }

    @Test
    void onlyAllowedUsersGetALinkAndALinkCannotBeChanged() throws Exception {
        // Étudiant qui n'a pas acheté le cours : pas de lien
        assertThat(send(outsider, "/api/lessons/resources/" + resource + "/stream-url").statusCode()).isEqualTo(403);

        String url = streamUrl(teacher);
        String forged = url + "A"; // signature modifiée
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base() + forged)).build(), HttpResponse.BodyHandlers.discarding())
                .statusCode()).isEqualTo(403);
        // Même signature pour une autre ressource : refusée
        String other = url.replace("/resources/" + resource + "/", "/resources/" + (resource + 1) + "/");
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base() + other)).build(), HttpResponse.BodyHandlers.discarding())
                .statusCode()).isEqualTo(403);
        // Sans signature : refusé
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base() + "/api/lessons/resources/" + resource + "/stream")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isIn(400, 403);
    }

    // ── Outils ──
    private String streamUrl(User u) throws Exception {
        HttpResponse<String> r = send(u, "/api/lessons/resources/" + resource + "/stream-url");
        assertThat(r.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(r.body()).get("url").asText();
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> send(User u, String path) throws Exception {
        String token = jwtUtil.generateToken(userDetails.loadUserByUsername(u.getEmail()));
        return http.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Authorization", "Bearer " + token).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private User user(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Stream " + id);
        u.setEmail("stream-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        return key.getKey().longValue();
    }
}
