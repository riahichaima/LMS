package com.course.lms.service;

import com.course.lms.security.jwt.JwtAuthEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Corrections CodeQL : noms de fichiers piégés (traversée de chemin) et réponse 401 en JSON sûr. */
class UploadSecurityTest {

    @Test
    void keepsSafeExtensions() {
        assertThat(FileStorageService.getFileExtension("cours.PDF")).isEqualTo("pdf");
        assertThat(FileStorageService.getFileExtension("video.mp4")).isEqualTo("mp4");
        assertThat(FileStorageService.getFileExtension("archive.tar.gz")).isEqualTo("gz");
    }

    @Test
    void neutralisesTrapExtensions() {
        // L'extension piégée contiendrait « / » ou « .. » : remplacée par « bin »
        assertThat(FileStorageService.getFileExtension("x./../../autre-dossier/f")).isEqualTo("bin");
        assertThat(FileStorageService.getFileExtension("evil.p\\..\\..\\f")).isEqualTo("bin");
        assertThat(FileStorageService.getFileExtension("sans-extension")).isEqualTo("bin");
        assertThat(FileStorageService.getFileExtension(null)).isEqualTo("bin");
        assertThat(FileStorageService.getFileExtension("trop.longueextension1")).isEqualTo("bin");
    }

    @Test
    void acceptsCourseMaterialTypesOnly() {
        assertThat(FileStorageService.requireAllowedExtension("Cours 1.PDF")).isEqualTo("pdf");
        assertThat(FileStorageService.requireAllowedExtension("seance.mp4")).isEqualTo("mp4");
        assertThat(FileStorageService.requireAllowedExtension("podcast.m4a")).isEqualTo("m4a");
        assertThat(FileStorageService.requireAllowedExtension("plan.pptx")).isEqualTo("pptx");
        for (String piege : new String[]{"page.html", "logo.svg", "x.htm", "flux.xml", "script.js", "a.xhtml", "sans-extension", null}) {
            assertThatThrownBy(() -> FileStorageService.requireAllowedExtension(piege))
                    .as(piege).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusedFileIsNeverWritten(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        FileStorageService service = new FileStorageService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadDir", dir.toString());
        var html = new org.springframework.mock.web.MockMultipartFile("file", "cours.html", "text/html",
                "<script>alert(document.domain)</script>".getBytes());
        assertThatThrownBy(() -> service.storePhysicalFileOnly(html)).isInstanceOf(IllegalArgumentException.class);
        try (var files = java.nio.file.Files.list(dir)) {
            assertThat(files).isEmpty();
        }
        var pdf = new org.springframework.mock.web.MockMultipartFile("file", "cours.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThat(service.storePhysicalFileOnly(pdf)).endsWith(".pdf");
    }

    @Test
    void refusesPathsOutsideUploadDir() {
        Path dir = Path.of("uploads", "videos");
        assertThat(FileStorageService.insideUploadDir(dir, "abc.pdf").getFileName().toString()).isEqualTo("abc.pdf");
        assertThatThrownBy(() -> FileStorageService.insideUploadDir(dir, "../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileStorageService.insideUploadDir(dir, "sous/dossier.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unauthorizedResponseIsValidEscapedJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x\"<script>alert(1)</script>");
        request.setServletPath("/api/x\"<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthEntryPoint().commence(request, response, new BadCredentialsException("Jeton \"invalide\""));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        // JSON valide : les guillemets sont échappés, la structure n'est pas cassée
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("path").asText()).isEqualTo("/api/x\"<script>alert(1)</script>");
        assertThat(json.get("message").asText()).isEqualTo("Jeton \"invalide\"");
    }
}
