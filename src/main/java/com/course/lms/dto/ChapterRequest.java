package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChapterRequest {

    @NotBlank(message = "Le titre du chapitre est obligatoire")
    private String title;

    @NotNull(message = "L'ID du cours est obligatoire")
    private Long courseId;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
