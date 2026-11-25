package com.course.lms.controller;

import com.course.lms.dto.LessonRequest;
import com.course.lms.dto.LessonResponse;
import com.course.lms.dto.LessonResourceRequest;
import com.course.lms.model.LessonResource;
import com.course.lms.repository.LessonResourceRepository;
import com.course.lms.service.LessonService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LessonController {

    @Autowired
    private LessonService lessonService;

    @Autowired
    private com.course.lms.service.FileStorageService fileStorageService;

    @Autowired
    private com.course.lms.repository.UserRepository userRepository;

    @Autowired
    private LessonResourceRepository lessonResourceRepository;

    @Autowired
    private com.course.lms.service.CourseAccessService courseAccessService;

    @Autowired
    private com.course.lms.service.ResourcePathGuard resourcePathGuard;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ENDPOINT UNIQUE ET ULTRA-ROBUSTE
    @PostMapping(value = "/teacher/lessons")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<LessonResponse> createLesson(
            @RequestParam(value = "lesson", required = false) String lessonParam,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        
        System.out.println("DEBUG: Entrée dans createLesson - Content-Type: " + servletRequest.getContentType());
        
        LessonRequest request = null;
        
        try {
            // Cas 1 : Multipart (Postman) -> Le JSON est dans le paramètre 'lesson'
            if (lessonParam != null && !lessonParam.isEmpty()) {
                request = objectMapper.readValue(lessonParam, LessonRequest.class);
                System.out.println("DEBUG: JSON lu depuis le paramètre 'lesson'");
            } 
            // Cas 2 : JSON Pur -> Le JSON est dans le corps de la requête
            else if (servletRequest.getContentType() != null && servletRequest.getContentType().contains("application/json")) {
                request = objectMapper.readValue(servletRequest.getInputStream(), LessonRequest.class);
                System.out.println("DEBUG: JSON lu depuis le corps de la requête (InputStream)");
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur de lecture du JSON: " + e.getMessage());
        }

        if (request == null) {
            throw new RuntimeException("Données manquantes. Envoyez une partie 'lesson' (multipart) ou un corps JSON.");
        }
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (files != null && !files.isEmpty()) {
            System.out.println("DEBUG: " + files.size() + " fichiers reçus.");
            processFiles(request, files);
        }

        return ResponseEntity.ok(lessonService.createLesson(request, email, isAdmin));
    }

    // 3. ESPACE PROFESSEUR - Modification
    @PutMapping(value = "/teacher/lessons/{id}", consumes = "application/json")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<LessonResponse> updateLesson(@PathVariable Long id, @Valid @RequestBody LessonRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(lessonService.updateLesson(id, request, auth.getName(), isAdmin));
    }

    @PutMapping(value = "/teacher/lessons/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<LessonResponse> updateLessonWithFile(
            @PathVariable Long id,
            @RequestPart("lesson") @Valid LessonRequest request,
            @RequestPart(value = "files", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        processFiles(request, files);

        return ResponseEntity.ok(lessonService.updateLesson(id, request, email, isAdmin));
    }

    @GetMapping("/teacher/lessons/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<LessonResponse> getTeacherLessonById(@PathVariable Long id) {
        courseAccessService.requireManageLesson(id);
        return ResponseEntity.ok(lessonService.getLessonById(id));
    }

    // 4. ESPACE PROFESSEUR - Suppression
    @DeleteMapping("/teacher/lessons/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteLesson(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        lessonService.deleteLesson(id, auth.getName(), isAdmin);
        return ResponseEntity.ok("Leçon supprimée avec succès");
    }

    @GetMapping("/lessons/resources/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id) {
        // Fichier d'un cours payant : réservé aux acheteurs, à l'enseignant et aux administrateurs
        courseAccessService.requireResourceAccess(id);
        try {
            LessonResource resource = lessonResourceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ressource non trouvée: " + id));

            if (resource.getFilePath() == null) {
                return ResponseEntity.notFound().build();
            }
            // Jamais un fichier hors du dossier uploads (chemin forgé ou ancienne donnée)
            if (!resourcePathGuard.isInsideUploads(resource.getFilePath())) {
                return ResponseEntity.notFound().build();
            }

            java.io.File physicalFile = new java.io.File(resource.getFilePath());
            if (!physicalFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Envoi en flux depuis le disque (pas de chargement en mémoire : vidéos lourdes, plusieurs étudiants)
            org.springframework.core.io.Resource fileResource = new org.springframework.core.io.FileSystemResource(physicalFile);

            MediaType mediaType;
            String contentType = resource.getMimeType();
            if (contentType != null) {
                try {
                    mediaType = MediaType.parseMediaType(contentType);
                } catch (Exception e) {
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
            } else {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String filename = resource.getTitle() != null ? resource.getTitle() : "file";
            if (resource.getFilePath().contains(".") && !filename.contains(".")) {
                String ext = resource.getFilePath().substring(resource.getFilePath().lastIndexOf("."));
                filename += ext;
            }

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            org.springframework.http.ContentDisposition.attachment()
                                    .filename(filename, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                    .contentType(mediaType)
                    .contentLength(physicalFile.length())
                    .body(fileResource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Autowired
    private com.course.lms.service.StreamLinkSigner streamLinkSigner;

    /**
     * Lien de lecture en streaming (vidéo, audio) : remis seulement à qui a le droit de lire la ressource
     * (acheteur, enseignant, administration), valable 4 h, signé (voir StreamLinkSigner).
     */
    @GetMapping("/lessons/resources/{id}/stream-url")
    public ResponseEntity<java.util.Map<String, Object>> streamUrl(@PathVariable Long id) {
        courseAccessService.requireResourceAccess(id);
        if (servableFile(id) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(java.util.Map.of("url", streamLinkSigner.path(id),
                "expiresInSeconds", com.course.lms.service.StreamLinkSigner.VALIDITY.toSeconds()));
    }

    /**
     * Lecture par morceaux (HTTP 206, en-tête Range) : la vidéo commence tout de suite et se charge pendant la
     * lecture. Pas de jeton (la balise vidéo ne peut pas l'envoyer) : la signature du lien en tient lieu.
     */
    @GetMapping("/lessons/resources/{id}/stream")
    public ResponseEntity<org.springframework.core.io.Resource> stream(@PathVariable Long id,
            @RequestParam long exp, @RequestParam String sig,
            @RequestParam(defaultValue = "false") boolean download) {
        if (!streamLinkSigner.isValid(id, exp, sig)) {
            throw new SecurityException("Lien de lecture invalide ou expiré : rechargez la page.");
        }
        LessonResource resource = lessonResourceRepository.findById(id).orElse(null);
        java.io.File file = servableFile(id);
        if (resource == null || file == null) return ResponseEntity.notFound().build();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (resource.getMimeType() != null) {
            try {
                mediaType = MediaType.parseMediaType(resource.getMimeType());
            } catch (Exception e) {
                // type inconnu : octet-stream
            }
        }
        String filename = resource.getTitle() != null ? resource.getTitle() : "fichier";
        String path = resource.getFilePath();
        if (path.contains(".") && !filename.contains(".")) filename += path.substring(path.lastIndexOf('.'));
        org.springframework.http.ContentDisposition disposition = (download
                ? org.springframework.http.ContentDisposition.attachment()
                : org.springframework.http.ContentDisposition.inline())
                .filename(filename, java.nio.charset.StandardCharsets.UTF_8).build();
        // Pas de longueur fixée ici : Spring répond 206 avec le morceau demandé (Range) ou 200 avec tout le fichier
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(mediaType)
                .body(new org.springframework.core.io.FileSystemResource(file));
    }

    /** Fichier d'une ressource, seulement s'il existe et se trouve dans le dossier uploads. */
    private java.io.File servableFile(Long id) {
        LessonResource resource = lessonResourceRepository.findById(id).orElse(null);
        if (resource == null || resource.getFilePath() == null) return null;
        if (!resourcePathGuard.isInsideUploads(resource.getFilePath())) return null;
        java.io.File file = new java.io.File(resource.getFilePath());
        return file.isFile() ? file : null;
    }

    // Helper method for file processing
    private void processFiles(LessonRequest request, java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        if (files != null && !files.isEmpty() && request.getResources() != null) {
            for (org.springframework.web.multipart.MultipartFile file : files) {
                for (com.course.lms.dto.LessonResourceRequest rr : request.getResources()) {
                    if (file.getOriginalFilename() != null && file.getOriginalFilename().equals(rr.getFileName())) {
                        try {
                            String filePath = fileStorageService.storePhysicalFileOnly(file);
                            rr.setFilePath(filePath);
                            rr.setMimeType(file.getContentType());
                            rr.setSize(file.getSize());
                        } catch (IllegalArgumentException e) {
                            throw e; // type de fichier refusé
                        } catch (Exception e) {
                            throw new RuntimeException("Erreur lors de l'upload du fichier " + file.getOriginalFilename() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    // ESPACE PUBLIC / ETUDIANT
    @GetMapping("/chapters/{chapterId}/lessons")
    public ResponseEntity<List<LessonResponse>> getLessonsByChapter(@PathVariable Long chapterId) {
        return ResponseEntity.ok(courseAccessService.protectLessonsOfChapter(chapterId, lessonService.getLessonsByChapterId(chapterId)));
    }

    // Voir une leçon spécifique
    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<LessonResponse> getLessonById(@PathVariable Long lessonId) {
        return ResponseEntity.ok(courseAccessService.protectLesson(lessonId, lessonService.getLessonById(lessonId)));
    }
}
