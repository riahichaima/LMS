package com.course.lms.dto;

import com.course.lms.model.LessonContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LessonRequest {

    @NotBlank(message = "Le titre de la leçon est obligatoire")
    private String title;

    @NotNull(message = "Le type de contenu est obligatoire")
    private LessonContentType contentType;

    // Pour les liens externes (YouTube, site web)
    private String contentUrl;
    
    // Pour les fichiers uploadés (PDF, vidéo du PC)
    private Long fileId;

    private String description;

    @NotNull(message = "L'ID du chapitre parent est obligatoire")
    private Long chapterId;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LessonContentType getContentType() { return contentType; }
    public void setContentType(LessonContentType contentType) { this.contentType = contentType; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}
