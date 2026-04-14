package com.course.lms.controller;

import com.course.lms.model.FileStorage;
import com.course.lms.model.User;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
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

    // Endpoint pour uploader un fichier PDF (Teacher/Admin)
    @PostMapping("/upload-pdf")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("Début upload PDF: " + file.getOriginalFilename());
            System.out.println("Content-Type détecté: " + file.getContentType());
            
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            System.out.println("Utilisateur: " + currentUser.getEmail());
            
            // Valider que c'est bien un PDF (plus flexible)
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.equals("application/pdf") && 
                !file.getOriginalFilename().toLowerCase().endsWith(".pdf"))) {
                System.out.println("Type de fichier invalide: " + contentType);
                return ResponseEntity.badRequest().body("Type de fichier invalide. Seuls les PDF sont autorisés.");
            }
            
            FileStorage savedFile = fileStorageService.storeFile(file, currentUser);
            System.out.println("Fichier sauvegardé avec ID: " + savedFile.getId());
            
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'upload PDF: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur lors de l'upload: " + e.getMessage());
        }
    }

    // Endpoint pour uploader un fichier PPT (Teacher/Admin)
    @PostMapping("/upload-ppt")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadPpt(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("Début upload PPT: " + file.getOriginalFilename());
            System.out.println("Content-Type détecté: " + file.getContentType());
            
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            
            // Valider que c'est bien un PPT (plus flexible)
            String contentType = file.getContentType();
            String fileName = file.getOriginalFilename().toLowerCase();
            if (contentType == null || 
                (!contentType.contains("application/vnd.ms-powerpoint") && 
                 !contentType.contains("application/vnd.openxmlformats-officedocument.presentationml") &&
                 !fileName.endsWith(".ppt") && !fileName.endsWith(".pptx"))) {
                return ResponseEntity.badRequest().body("Type de fichier invalide. Seuls les PPT/PPTX sont autorisés.");
            }
            
            FileStorage savedFile = fileStorageService.storeFile(file, currentUser);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'upload PPT: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur lors de l'upload: " + e.getMessage());
        }
    }

    // Endpoint pour uploader une vidéo (Teacher/Admin)
    @PostMapping("/upload-video")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("=== DÉBUT UPLOAD VIDÉO ===");
            System.out.println("Nom du fichier: " + file.getOriginalFilename());
            System.out.println("Content-Type détecté: " + file.getContentType());
            System.out.println("Taille du fichier: " + file.getSize() + " bytes");
            
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            System.out.println("Utilisateur: " + currentUser.getEmail());
            
            // Valider que c'est bien une vidéo (plus flexible)
            String contentType = file.getContentType();
            String fileName = file.getOriginalFilename().toLowerCase();
            
            System.out.println("=== VALIDATION ===");
            System.out.println("Content-Type: '" + contentType + "'");
            System.out.println("Nom du fichier: '" + fileName + "'");
            
            boolean isValidVideo = false;
            String reason = "";
            
            // Vérifier par content-type
            if (contentType != null && contentType.contains("video/")) {
                isValidVideo = true;
                reason = "Content-Type valide: " + contentType;
            }
            // Vérifier par extension
            else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || 
                     fileName.endsWith(".mov") || fileName.endsWith(".wmv") || 
                     fileName.endsWith(".flv") || fileName.endsWith(".webm") || 
                     fileName.endsWith(".mkv")) {
                isValidVideo = true;
                reason = "Extension valide: " + fileName.substring(fileName.lastIndexOf('.'));
            }
            
            System.out.println("Résultat validation: " + isValidVideo);
            System.out.println("Raison: " + reason);
            
            if (!isValidVideo) {
                System.out.println("=== ÉCHEC VALIDATION ===");
                return ResponseEntity.badRequest().body("Type de fichier invalide. Seules les vidéos sont autorisées (mp4, avi, mov, wmv, flv, webm, mkv). Détecté: contentType=" + contentType + ", fileName=" + fileName);
            }
            
            FileStorage savedFile = fileStorageService.storeFile(file, currentUser);
            System.out.println("Fichier sauvegardé avec ID: " + savedFile.getId());
            System.out.println("=== FIN UPLOAD VIDÉO ===");
            
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'upload Vidéo: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur lors de l'upload: " + e.getMessage());
        }
    }

    // Endpoint de test sans validation (pour diagnostiquer)
    @PostMapping("/upload-test")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadTest(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("=== UPLOAD TEST SANS VALIDATION ===");
            System.out.println("Nom du fichier: " + file.getOriginalFilename());
            System.out.println("Content-Type détecté: " + file.getContentType());
            System.out.println("Taille du fichier: " + file.getSize() + " bytes");
            System.out.println("Fichier vide: " + file.isEmpty());
            
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            
            FileStorage savedFile = fileStorageService.storeFile(file, currentUser);
            System.out.println("Fichier sauvegardé avec ID: " + savedFile.getId());
            
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'upload test: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur lors de l'upload test: " + e.getMessage());
        }
    }

    // Endpoint générique pour uploader n'importe quel fichier (Teacher/Admin)
    @PostMapping("/upload")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("Début upload générique: " + file.getOriginalFilename());
            
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
            
            FileStorage savedFile = fileStorageService.storeFile(file, currentUser);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'upload générique: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur lors de l'upload: " + e.getMessage());
        }
    }

    // Endpoint pour télécharger un fichier (tout le monde)
    @GetMapping("/download/{fileName}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable String fileName) {
        try {
            System.out.println("=== DÉBUT TÉLÉCHARGEMENT ===");
            System.out.println("Fichier demandé: " + fileName);
            
            FileStorage fileStorage = fileStorageService.getFileByStoredName(fileName);
            System.out.println("Fichier trouvé en base: " + fileStorage.getOriginalFileName());
            System.out.println("Content-Type: " + fileStorage.getContentType());
            System.out.println("Chemin physique: " + fileStorage.getFilePath());
            
            // Vérifier si le fichier existe physiquement
            java.io.File physicalFile = new java.io.File(fileStorage.getFilePath());
            System.out.println("Fichier physique existe: " + physicalFile.exists());
            System.out.println("Taille physique: " + physicalFile.length() + " bytes");
            
            if (!physicalFile.exists()) {
                System.err.println("ERREUR: Le fichier physique n'existe pas!");
                return ResponseEntity.notFound().build();
            }
            
            byte[] data = fileStorageService.getFileContent(fileName);
            System.out.println("Taille des données lues: " + data.length + " bytes");
            
            // Vérifier l'intégrité des données
            if (data.length < 100) {
                System.err.println("ERREUR: Fichier trop petit, probablement corrompu!");
                return ResponseEntity.internalServerError().build();
            }
            
            // Vérifier la signature du fichier
            if (data.length > 4) {
                String signature = new String(data, 0, 4);
                System.out.println("Signature du fichier: " + signature);
                
                if (signature.startsWith("PK")) {
                    System.out.println("Fichier ZIP détecté (PPTX/DOCX/XLSX)");
                } else if (signature.startsWith("%PDF")) {
                    System.out.println("Fichier PDF détecté");
                }
            }
            
            ByteArrayResource resource = new ByteArrayResource(data);
            
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
            
            System.out.println("Content-Type final: " + mediaType.toString());
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileStorage.getOriginalFileName() + "\"")
                    .contentType(mediaType)
                    .contentLength(data.length)
                    .body(resource);
        } catch (Exception e) {
            System.err.println("Erreur lors du téléchargement: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint alternatif pour téléchargement binaire pur
    @GetMapping("/download-binary/{fileName}")
    public ResponseEntity<byte[]> downloadBinaryFile(@PathVariable String fileName) {
        try {
            System.out.println("=== TÉLÉCHARGEMENT BINAIRE PUR ===");
            System.out.println("Fichier demandé: " + fileName);
            
            FileStorage fileStorage = fileStorageService.getFileByStoredName(fileName);
            System.out.println("Fichier trouvé en base: " + fileStorage.getOriginalFileName());
            
            byte[] data = fileStorageService.getFileContent(fileName);
            System.out.println("Taille des données: " + data.length + " bytes");
            
            // Enlever les headers problématiques
            return ResponseEntity.ok()
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Disposition", "attachment; filename=\"" + fileStorage.getOriginalFileName() + "\"")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .body(data);
        } catch (Exception e) {
            System.err.println("Erreur lors du téléchargement binaire: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint de streaming direct (solution finale)
    @GetMapping("/stream/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> streamFile(@PathVariable String fileName) {
        try {
            System.out.println("=== STREAMING DIRECT ===");
            System.out.println("Fichier demandé: " + fileName);
            
            FileStorage fileStorage = fileStorageService.getFileByStoredName(fileName);
            System.out.println("Fichier trouvé en base: " + fileStorage.getOriginalFileName());
            
            java.io.File physicalFile = new java.io.File(fileStorage.getFilePath());
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(physicalFile.toURI());
            
            if (!resource.exists() || !resource.isReadable()) {
                System.err.println("Fichier non lisible: " + fileName);
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileStorage.getOriginalFileName() + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .body(resource);
        } catch (Exception e) {
            System.err.println("Erreur lors du streaming: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("Erreur lors de la récupération des fichiers: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // Endpoint pour lister TOUS les fichiers (admin/debug)
    @GetMapping("/all-files")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<FileStorage>> getAllFiles() {
        try {
            List<FileStorage> files = fileStorageService.getAllFiles();
            System.out.println("=== TOUS LES FICHIERS EN BASE ===");
            for (FileStorage file : files) {
                System.out.println("ID: " + file.getId() + 
                    ", Original: " + file.getOriginalFileName() + 
                    ", Stored: " + file.getStoredFileName() + 
                    ", DownloadURL: " + file.getDownloadUrl());
            }
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération de tous les fichiers: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // Endpoint public pour voir les informations des fichiers (sans restriction)
    @GetMapping("/files-info")
    public ResponseEntity<?> getFilesInfo() {
        try {
            List<FileStorage> files = fileStorageService.getAllFiles();
            System.out.println("=== INFORMATIONS DES FICHIERS (PUBLIC) ===");
            
            // Retourner seulement les informations nécessaires pour le debug
            return ResponseEntity.ok(files.stream().map(file -> {
                return new Object() {
                    public final Long id = file.getId();
                    public final String originalFileName = file.getOriginalFileName();
                    public final String storedFileName = file.getStoredFileName();
                    public final String downloadUrl = file.getDownloadUrl();
                    public final String contentType = file.getContentType();
                    public final Long fileSize = file.getFileSize();
                };
            }).collect(java.util.stream.Collectors.toList()));
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des infos fichiers: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erreur: " + e.getMessage());
        }
    }
}
