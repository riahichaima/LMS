package com.course.lms.controller;

import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.security.jwt.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fichiers déposés : un enseignant ne peut pas déposer une page HTML/SVG (JavaScript exécuté sur le domaine de
 * l'API), et un ancien fichier de ce type déjà présent dans /uploads est servi en téléchargement, dans un bac à sable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadsProtectionIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserDetailsService userDetails;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Path> created = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        for (Path p : created) Files.deleteIfExists(p);
    }

    @Test
    void htmlAndSvgUploadsAreRefused() throws Exception {
        User teacher = teacher();
        assertThat(upload(teacher, "piege.html", "text/html", "<script>alert(document.domain)</script>").statusCode()).isEqualTo(400);
        assertThat(upload(teacher, "logo.svg", "image/svg+xml", "<svg onload=alert(1)></svg>").statusCode()).isEqualTo(400);
        HttpResponse<String> pdf = upload(teacher, "support.pdf", "application/pdf", "%PDF-1.4 test");
        assertThat(pdf.statusCode()).isEqualTo(200);
        String stored = new com.fasterxml.jackson.databind.ObjectMapper().readTree(pdf.body()).get("filePath").asText();
        created.add(Path.of(stored));
    }

    @Test
    void oldRiskyFilesAreServedAsSandboxedDownloads() throws Exception {
        String name = "test-" + UUID.randomUUID();
        Path html = file(name + ".html", "<script>alert(document.domain)</script>");
        Path pdf = file(name + ".pdf", "%PDF-1.4 test");

        HttpResponse<String> risky = get("/uploads/" + html.getFileName());
        assertThat(risky.statusCode()).isEqualTo(200);
        assertThat(risky.headers().firstValue("Content-Disposition")).hasValue("attachment");
        assertThat(risky.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(v -> assertThat(v).startsWith("sandbox"));

        HttpResponse<String> normal = get("/uploads/" + pdf.getFileName());
        assertThat(normal.statusCode()).isEqualTo(200);
        assertThat(normal.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(normal.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("application/pdf"));
    }

    // ── Outils ──
    private User teacher() {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Upload " + id);
        u.setEmail("upload-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName("TEACHER").orElseThrow());
        return users.save(u);
    }

    private Path file(String name, String content) throws Exception {
        Path dir = Path.of("uploads");
        Files.createDirectories(dir);
        Path p = Files.writeString(dir.resolve(name), content);
        created.add(p);
        return p;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> upload(User u, String fileName, String type, String content) throws Exception {
        String boundary = "lms" + UUID.randomUUID();
        String crlf = "\r\n";
        String body = "--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf
                + "Content-Type: " + type + crlf + crlf
                + content + crlf
                + "--" + boundary + "--" + crlf;
        String token = jwtUtil.generateToken(userDetails.loadUserByUsername(u.getEmail()));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/files/upload"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
