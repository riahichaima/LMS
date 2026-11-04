package com.course.lms.service;

import com.course.lms.model.FileStorage;
import com.course.lms.model.User;
import com.course.lms.repository.FileStorageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Autowired
    private FileStorageRepository fileStorageRepository;

    public FileStorage storeFile(MultipartFile file, User uploadedBy) throws IOException {
        // Créer le répertoire s'il n'existe pas
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Générer un nom de fichier unique
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String storedFileName = UUID.randomUUID().toString() + "." + extension;
        String filePath = uploadPath.resolve(storedFileName).toString();

        // Sauvegarder le fichier physiquement
        Files.copy(file.getInputStream(), Paths.get(filePath));

        // Créer l'URL de téléchargement
        String downloadUrl = "http://localhost:8090/api/files/download/" + storedFileName;

        // Sauvegarder en base de données
        FileStorage fileStorage = new FileStorage(
                originalFileName,
                storedFileName,
                file.getContentType(),
                file.getSize(),
                filePath,
                downloadUrl,
                uploadedBy
        );

        return fileStorageRepository.save(fileStorage);
    }

    public String storePhysicalFileOnly(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            originalFileName = "file";
        }
        String extension = getFileExtension(originalFileName);
        String storedFileName = UUID.randomUUID().toString() + "." + extension;
        String filePath = uploadPath.resolve(storedFileName).toString();

        Files.copy(file.getInputStream(), Paths.get(filePath));
        return filePath;
    }

    public FileStorage getFileByStoredName(String storedFileName) {
        return fileStorageRepository.findByStoredFileName(storedFileName)
                .orElseThrow(() -> new RuntimeException("Fichier non trouvé: " + storedFileName));
    }

    public byte[] getFileContent(String storedFileName) throws IOException {
        FileStorage fileStorage = getFileByStoredName(storedFileName);
        return Files.readAllBytes(Paths.get(fileStorage.getFilePath()));
    }

    public void deleteFile(String storedFileName) throws IOException {
        FileStorage fileStorage = getFileByStoredName(storedFileName);
        
        // Supprimer le fichier physique
        Files.deleteIfExists(Paths.get(fileStorage.getFilePath()));
        
        // Supprimer de la base de données
        fileStorageRepository.deleteByStoredFileName(storedFileName);
    }

    public List<FileStorage> getFilesByUser(User user) {
        return fileStorageRepository.findByUploadedById(user.getId());
    }

    public List<FileStorage> getAllFiles() {
        return fileStorageRepository.findAll();
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
