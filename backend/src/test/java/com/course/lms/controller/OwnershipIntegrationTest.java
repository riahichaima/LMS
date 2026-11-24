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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sur un vrai serveur : une leçon ne peut pas être déplacée dans le cours d'un autre enseignant (S-3), un refus
 * d'accès répond 403 sans l'e-mail d'autrui, et le hash du mot de passe n'apparaît jamais dans une réponse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OwnershipIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserDetailsService userDetails;

    private final HttpClient http = HttpClient.newHttpClient();
    private User teacherA, teacherB;
    private long courseB, chapterA1, chapterA2, chapterB, lessonA;
    private Path uploaded;

    @BeforeEach
    void data() {
        teacherA = teacher();
        teacherB = teacher();
        long courseA = course(teacherA);
        courseB = course(teacherB);
        chapterA1 = insert("INSERT INTO chapters (title, course_id, created_at) VALUES ('A1', ?, NOW())", courseA);
        chapterA2 = insert("INSERT INTO chapters (title, course_id, created_at) VALUES ('A2', ?, NOW())", courseA);
        chapterB = insert("INSERT INTO chapters (title, course_id, created_at) VALUES ('B', ?, NOW())", courseB);
        lessonA = insert("INSERT INTO lessons (title, chapter_id, created_at) VALUES ('Leçon A', ?, NOW())", chapterA1);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (uploaded != null) Files.deleteIfExists(uploaded);
    }

    @Test
    void aLessonCannotBeMovedIntoAnotherTeachersCourse() throws Exception {
        HttpResponse<String> refused = send(teacherA, "PUT", "/api/teacher/lessons/" + lessonA,
                "{\"title\":\"Leçon A\",\"chapterId\":" + chapterB + ",\"resources\":[]}");
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.body()).doesNotContain(teacherB.getEmail());
        assertThat(chapterOf(lessonA)).isEqualTo(chapterA1);

        // Vers un autre chapitre de son propre cours : autorisé
        assertThat(send(teacherA, "PUT", "/api/teacher/lessons/" + lessonA,
                "{\"title\":\"Leçon A\",\"chapterId\":" + chapterA2 + ",\"resources\":[]}").statusCode()).isEqualTo(200);
        assertThat(chapterOf(lessonA)).isEqualTo(chapterA2);
    }

    @Test
    void refusedAccessIsA403WithoutTheOwnersEmail() throws Exception {
        HttpResponse<String> delete = send(teacherA, "DELETE", "/api/teacher/courses/" + courseB, null);
        assertThat(delete.statusCode()).isEqualTo(403);
        assertThat(delete.body()).doesNotContain(teacherB.getEmail());
        assertThat(courses.findById(courseB)).isPresent();

        HttpResponse<String> chapter = send(teacherA, "PUT", "/api/teacher/chapters/" + chapterB, "{\"title\":\"x\"}");
        assertThat(chapter.statusCode()).isEqualTo(403);
        assertThat(chapter.body()).doesNotContain(teacherB.getEmail());
    }

    @Test
    void passwordHashIsNeverInAResponse() throws Exception {
        HttpResponse<String> upload = upload(teacherA, "support.pdf", "%PDF-1.4 test");
        assertThat(upload.statusCode()).isEqualTo(200);
        uploaded = Path.of(new ObjectMapper().readTree(upload.body()).get("filePath").asText());
        assertThat(upload.body()).doesNotContain("\"password\"").doesNotContain("$2a$");

        HttpResponse<String> mine = send(teacherA, "GET", "/api/files/my-files", null);
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).doesNotContain("\"password\"").doesNotContain("$2a$");
    }

    // ── Outils ──
    private User teacher() {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Propriété " + id);
        u.setEmail("prop-" + id + "@test.lms");
        u.setPassword("$2a$10$abcdefghijklmnopqrstuuJ0nC7Ff8rWkqYH0r1fz1mG0yS6aWnhi");
        u.setEnabled(true);
        u.setRole(roles.findByName("TEACHER").orElseThrow());
        return users.save(u);
    }

    private long course(User teacher) {
        Course c = new Course();
        c.setTitle("Cours " + teacher.getName());
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        c.setPricingType("FREE");
        return courses.save(c).getId();
    }

    private long chapterOf(long lesson) {
        return jdbc.queryForObject("SELECT chapter_id FROM lessons WHERE id = ?", Long.class, lesson);
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

    private String token(User u) {
        return jwtUtil.generateToken(userDetails.loadUserByUsername(u.getEmail()));
    }

    private HttpResponse<String> send(User u, String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token(u)).header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> upload(User u, String fileName, String content) throws Exception {
        String boundary = "lms" + UUID.randomUUID();
        String crlf = "\r\n";
        String body = "--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf
                + "Content-Type: application/pdf" + crlf + crlf
                + content + crlf
                + "--" + boundary + "--" + crlf;
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/files/upload"))
                .header("Authorization", "Bearer " + token(u))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
