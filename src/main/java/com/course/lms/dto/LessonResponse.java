package com.course.lms.dto;

import com.course.lms.model.LessonContentType;

public class LessonResponse {

    private Long id;
    private String title;
    private LessonContentType contentType;
    private String contentUrl; // Pour les liens externes
    private String description;
    private Long chapterId;
    private String courseName;  // ← Nom du cours
    private String chapterTitle; // ← Nom du chapitre
    
    // Informations du fichier uploadé
    private Long fileId;
    private String originalFileName;
    private String storedFileName;
    private String fileDownloadUrl;
    private Long fileSize;
    private String fileContentType;

    public LessonResponse(Long id, String title, LessonContentType contentType, String contentUrl, 
                       String description, Long chapterId, String courseName, String chapterTitle,
                       Long fileId, String originalFileName, String storedFileName, 
                       String fileDownloadUrl, Long fileSize, String fileContentType) {
        this.id = id;
        this.title = title;
        this.contentType = contentType;
        this.contentUrl = contentUrl;
        this.description = description;
        this.chapterId = chapterId;
        this.courseName = courseName;
        this.chapterTitle = chapterTitle;
        this.fileId = fileId;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.fileDownloadUrl = fileDownloadUrl;
        this.fileSize = fileSize;
        this.fileContentType = fileContentType;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LessonContentType getContentType() { return contentType; }
    public void setContentType(LessonContentType contentType) { this.contentType = contentType; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getFileDownloadUrl() { return fileDownloadUrl; }
    public void setFileDownloadUrl(String fileDownloadUrl) { this.fileDownloadUrl = fileDownloadUrl; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileContentType() { return fileContentType; }
    public void setFileContentType(String fileContentType) { this.fileContentType = fileContentType; }
}
