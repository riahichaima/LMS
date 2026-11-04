package com.course.lms.dto;

import java.util.List;

public class ChapterResponse {

    private Long id;
    private String title;
    private Long courseId;
    private String description;
    private Integer orderIndex;
    private List<LessonResponse> lessons;

    public ChapterResponse() {}

    public ChapterResponse(Long id, String title, Long courseId, String description, Integer orderIndex) {
        this.id = id;
        this.title = title;
        this.courseId = courseId;
        this.description = description;
        this.orderIndex = orderIndex;
    }

    public ChapterResponse(Long id, String title, Long courseId, String description, Integer orderIndex, List<LessonResponse> lessons) {
        this.id = id;
        this.title = title;
        this.courseId = courseId;
        this.description = description;
        this.orderIndex = orderIndex;
        this.lessons = lessons;
    }

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
    public List<LessonResponse> getLessons() { return lessons; }
    public void setLessons(List<LessonResponse> lessons) { this.lessons = lessons; }
}
