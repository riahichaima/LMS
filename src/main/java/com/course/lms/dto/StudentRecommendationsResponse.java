package com.course.lms.dto;

import java.time.LocalDateTime;
import java.util.List;

public class StudentRecommendationsResponse {
    private Long studentId;
    private Long courseId;
    private LocalDateTime recommendationDate;
    private List<String> recommendedLessons;
    private List<String> weakAreas;
    private List<String> strongAreas;
    private String personalizedAdvice;
    private String recommendedLevel;

    public StudentRecommendationsResponse() {}

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public LocalDateTime getRecommendationDate() { return recommendationDate; }
    public void setRecommendationDate(LocalDateTime recommendationDate) { this.recommendationDate = recommendationDate; }

    public List<String> getRecommendedLessons() { return recommendedLessons; }
    public void setRecommendedLessons(List<String> recommendedLessons) { this.recommendedLessons = recommendedLessons; }

    public List<String> getWeakAreas() { return weakAreas; }
    public void setWeakAreas(List<String> weakAreas) { this.weakAreas = weakAreas; }

    public List<String> getStrongAreas() { return strongAreas; }
    public void setStrongAreas(List<String> strongAreas) { this.strongAreas = strongAreas; }

    public String getPersonalizedAdvice() { return personalizedAdvice; }
    public void setPersonalizedAdvice(String personalizedAdvice) { this.personalizedAdvice = personalizedAdvice; }

    public String getRecommendedLevel() { return recommendedLevel; }
    public void setRecommendedLevel(String recommendedLevel) { this.recommendedLevel = recommendedLevel; }
}
