package com.course.lms.controller;

import com.course.lms.model.FileStorage;
import com.course.lms.model.User;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private UserRepository userRepository;

    // Endpoint générique pour uploader n'importe quel fichier (Teacher/Admin)
    @PostMapping("/upload")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            
            FileStorage savedFile = fileStorageService.storeFile(file, currentUser);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur lors de l'upload: " + e.getMessage());
        }
    }

    // Endpoint pour télécharger un fichier (tout le monde)
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            FileStorage fileStorage = fileStorageService.getFileByStoredName(fileName);
            
            // Vérifier si le fichier existe physiquement
            java.io.File physicalFile = new java.io.File(fileStorage.getFilePath());
            if (!physicalFile.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            // Envoi en flux depuis le disque (pas de chargement complet en mémoire)
            Resource resource = new FileSystemResource(physicalFile);
            
            // Déterminer le content-type correct
            MediaType mediaType;
            String contentType = fileStorage.getContentType();
            
            if (contentType != null) {
                try {
                    mediaType = MediaType.parseMediaType(contentType);
                } catch (Exception e) {
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
            } else {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            
            // Pour les fichiers Office, s'assurer du bon content-type
            if (fileName.toLowerCase().endsWith(".pptx") || fileName.toLowerCase().endsWith(".ppt")) {
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            } else if (fileName.toLowerCase().endsWith(".docx") || fileName.toLowerCase().endsWith(".doc")) {
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            } else if (fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls")) {
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                            .filename(fileStorage.getOriginalFileName() != null ? fileStorage.getOriginalFileName() : fileName,
                                    java.nio.charset.StandardCharsets.UTF_8).build().toString())
                    .contentType(mediaType)
                    .contentLength(physicalFile.length())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint pour lister les fichiers uploadés par l'utilisateur connecté
    @GetMapping("/my-files")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<FileStorage>> getMyFiles() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            
            List<FileStorage> files = fileStorageService.getFilesByUser(currentUser);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Endpoint pour lister TOUS les fichiers (admin uniquement)
    @GetMapping("/all-files")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FileStorage>> getAllFiles() {
        try {
            List<FileStorage> files = fileStorageService.getAllFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
