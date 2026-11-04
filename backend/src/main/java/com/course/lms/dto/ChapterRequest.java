package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ChapterRequest {

    private Long id; // Renseigné lors d'une modification : chapitre existant à conserver

    @NotBlank(message = "Le titre du chapitre est obligatoire")
    private String title;

    private Long courseId; // Optionnel car inclus dans CourseRequest
    private String description;
    private Integer orderIndex;
    
    private List<LessonRequest> lessons;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public List<LessonRequest> getLessons() { return lessons; }
    public void setLessons(List<LessonRequest> lessons) { this.lessons = lessons; }
}
