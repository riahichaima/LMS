package com.course.lms.repository;

import com.course.lms.model.FileStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileStorageRepository extends JpaRepository<FileStorage, Long> {
    
    Optional<FileStorage> findByStoredFileName(String storedFileName);
    
    List<FileStorage> findByUploadedById(Long userId);
    
    List<FileStorage> findByContentType(String contentType);
    
    void deleteByStoredFileName(String storedFileName);
}
