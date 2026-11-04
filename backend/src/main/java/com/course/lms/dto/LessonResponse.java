package com.course.lms.dto;

import java.util.List;

public class LessonResponse {

    private Long id;
    private String title;
    private String description;
    private Long chapterId;
    private String courseName;
    private String chapterTitle;
    private Integer orderIndex;
    private List<LessonResourceResponse> resources;

    public LessonResponse() {}

    public LessonResponse(Long id, String title, String description, Long chapterId, 
                          String courseName, String chapterTitle, Integer orderIndex, List<LessonResourceResponse> resources) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.chapterId = chapterId;
        this.courseName = courseName;
        this.chapterTitle = chapterTitle;
        this.orderIndex = orderIndex;
        this.resources = resources;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public List<LessonResourceResponse> getResources() { return resources; }
    public void setResources(List<LessonResourceResponse> resources) { this.resources = resources; }
}
