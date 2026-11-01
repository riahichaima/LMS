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
    public ResponseEntity<org.springframework.core.io.ByteArrayResource> downloadResource(@PathVariable Long id) {
        // Fichier d'un cours payant : réservé aux acheteurs, à l'enseignant et aux administrateurs
        courseAccessService.requireResourceAccess(id);
        try {
            LessonResource resource = lessonResourceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ressource non trouvée: " + id));

            if (resource.getFilePath() == null) {
                return ResponseEntity.notFound().build();
            }

            java.io.File physicalFile = new java.io.File(resource.getFilePath());
            if (!physicalFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = java.nio.file.Files.readAllBytes(physicalFile.toPath());
            org.springframework.core.io.ByteArrayResource byteArrayResource = new org.springframework.core.io.ByteArrayResource(data);

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
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .contentLength(data.length)
                    .body(byteArrayResource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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
