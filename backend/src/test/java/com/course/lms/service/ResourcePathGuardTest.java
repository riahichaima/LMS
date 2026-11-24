package com.course.lms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S-1 : un chemin de ressource envoyé par le navigateur ne sort jamais du dossier uploads. */
class ResourcePathGuardTest {

    @Test
    void uploadsRootIsTheUploadsFolder(@TempDir Path dir) {
        Path videos = dir.resolve("uploads").resolve("videos");
        assertThat(ResourcePathGuard.uploadsRoot(videos)).isEqualTo(dir.resolve("uploads").toAbsolutePath().normalize());
    }

    @Test
    void acceptsOnlyFilesInsideUploads(@TempDir Path dir) {
        ResourcePathGuard guard = new ResourcePathGuard(dir.resolve("uploads").resolve("videos").toString());
        String inside = dir.resolve("uploads").resolve("videos").resolve("a.pdf").toString();
        String otherFolder = dir.resolve("uploads").resolve("courses").resolve("b.pdf").toString();

        assertThat(guard.isInsideUploads(inside)).isTrue();
        assertThat(guard.isInsideUploads(otherFolder)).isTrue();
        assertThat(guard.isInsideUploads(dir.resolve("secret.txt").toString())).isFalse();
        assertThat(guard.isInsideUploads(dir.resolve("uploads").resolve("..").resolve("secret.txt").toString())).isFalse();
        assertThat(guard.isInsideUploads(dir.resolve("uploads").toString())).isFalse();
        assertThat(guard.isInsideUploads("C:\\Windows\\win.ini")).isFalse();
        assertThat(guard.isInsideUploads("/proc/self/environ")).isFalse();
        assertThat(guard.isInsideUploads(null)).isFalse();
        assertThat(guard.isInsideUploads(" ")).isFalse();
    }

    @Test
    void forgedPathIsRefusedButStoredOneIsKept(@TempDir Path dir) {
        ResourcePathGuard guard = new ResourcePathGuard(dir.resolve("uploads").resolve("videos").toString());
        String inside = dir.resolve("uploads").resolve("videos").resolve("a.pdf").toString();

        assertThat(guard.requireAllowed(inside, Set.of())).isEqualTo(inside);
        assertThat(guard.requireAllowed(null, Set.of())).isNull();
        assertThatThrownBy(() -> guard.requireAllowed("/proc/self/environ", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.requireAllowed(dir.resolve("secret.txt").toString(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // Ressource déjà enregistrée pour cette leçon : conservée telle quelle (anciennes données)
        assertThat(guard.requireAllowed("/ancien/chemin/cours.pdf", Set.of("/ancien/chemin/cours.pdf")))
                .isEqualTo("/ancien/chemin/cours.pdf");
    }
}
