package com.course.lms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Chemins des fichiers de cours : seuls les fichiers déposés sur la plateforme (dossier « uploads ») sont acceptés.
 *
 * Le navigateur renvoie le filePath des ressources déjà importées quand un cours ou une leçon est modifié.
 * Sans ce contrôle, un enseignant pourrait y écrire n'importe quel chemin du serveur (ex. /proc/self/environ)
 * puis le télécharger comme une ressource de son cours.
 */
@Component
public class ResourcePathGuard {

    private final Path uploadsRoot;

    public ResourcePathGuard(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadsRoot = uploadsRoot(Paths.get(uploadDir));
    }

    /** Dossier « uploads » qui contient le dossier d'upload (ex. /app/uploads pour /app/uploads/videos). */
    static Path uploadsRoot(Path uploadDir) {
        Path absolute = uploadDir.toAbsolutePath().normalize();
        for (Path p = absolute; p != null; p = p.getParent()) {
            if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("uploads")) {
                return p;
            }
        }
        return absolute;
    }

    /** Vrai si le chemin désigne un fichier situé dans le dossier uploads (après résolution des « .. »). */
    public boolean isInsideUploads(String filePath) {
        if (filePath == null || filePath.isBlank()) return false;
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            return !path.equals(uploadsRoot) && path.startsWith(uploadsRoot);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Chemin d'une ressource envoyé par le navigateur. Accepté s'il est dans le dossier uploads, ou s'il était déjà
     * celui d'une ressource de cette leçon (ressource conservée telle quelle). Sinon : refus (HTTP 400).
     */
    public String requireAllowed(String filePath, Set<String> alreadyStored) {
        if (filePath == null || filePath.isBlank()) return null;
        if (alreadyStored.contains(filePath) || isInsideUploads(filePath)) return filePath;
        throw new IllegalArgumentException("Chemin de fichier refusé : seuls les fichiers déposés sur la plateforme sont acceptés.");
    }
}
