package com.course.lms.controller;

import com.course.lms.model.Course;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.security.jwt.JwtUtil;
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
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-1 sur un vrai serveur : un enseignant ne peut ni enregistrer un chemin de fichier hors du dossier uploads
 * dans une ressource de sa leçon, ni télécharger un fichier du serveur à travers une ressource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourcePathIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserDetailsService userDetails;

    private final HttpClient http = HttpClient.newHttpClient();
    private User teacher;
    private long lesson;
    /** Vrai fichier du serveur, hors du dossier uploads (le pom.xml du backend pendant les tests). */
    private final String serverFile = Path.of("pom.xml").toAbsolutePath().toString();

    @BeforeEach
    void data() {
        teacher = teacher();
        Course c = new Course();
        c.setTitle("Cours chemins");
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        c.setPricingType("FREE");
        long course = courses.save(c).getId();
        long chapter = insert("INSERT INTO chapters (title, course_id, created_at) VALUES ('Chapitre', ?, NOW())", course);
        lesson = insert("INSERT INTO lessons (title, chapter_id, created_at) VALUES ('Leçon', ?, NOW())", chapter);
    }

    @Test
    void forgedFilePathIsRefusedWhenSavingALesson() throws Exception {
        String body = "{\"title\":\"Leçon\",\"resources\":[{\"resourceType\":\"PDF\",\"title\":\"probe\",\"filePath\":"
                + json(serverFile) + "}]}";
        assertThat(send("PUT", "/api/teacher/lessons/" + lesson, body).statusCode()).isEqualTo(400);
        Integer stored = jdbc.queryForObject("SELECT COUNT(*) FROM lesson_resources WHERE lesson_id = ? AND file_path = ?",
                Integer.class, lesson, serverFile);
        assertThat(stored).isZero();
    }

    @Test
    void serverFileIsNeverDownloadedThroughAResource() throws Exception {
        // Ressource déjà en base avec un chemin hors uploads (donnée forgée avant le correctif)
        long resource = insert("INSERT INTO lesson_resources (title, resource_type, file_path, lesson_id, created_at) "
                + "VALUES ('probe', 'PDF', ?, ?, NOW())", serverFile, lesson);
        HttpResponse<String> response = send("GET", "/api/lessons/resources/" + resource + "/download", null);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("<artifactId>");
    }

    // ── Outils ──
    private User teacher() {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Chemins " + id);
        u.setEmail("chemins-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName("TEACHER").orElseThrow());
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

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        String token = jwtUtil.generateToken(userDetails.loadUserByUsername(teacher.getEmail()));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
