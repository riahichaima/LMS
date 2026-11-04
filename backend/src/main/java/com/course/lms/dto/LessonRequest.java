package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class LessonRequest {

    private Long id; // Renseigné lors d'une modification : leçon existante à conserver

    @NotBlank(message = "Le titre de la leçon est obligatoire")
    private String title;

    private String description;

    private Long chapterId; // Optionnel car inclus dans ChapterRequest
    private Integer orderIndex;
    
    private List<LessonResourceRequest> resources;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public List<LessonResourceRequest> getResources() { return resources; }
    public void setResources(List<LessonResourceRequest> resources) { this.resources = resources; }
}
