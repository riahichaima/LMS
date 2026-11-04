package com.course.lms.dto;

import com.course.lms.model.DifficultyLevel;
import com.course.lms.model.ResourceType;
import java.util.List;

public class QuizGenerationRequest {
    private List<Long> chapterIds;
    private List<Long> lessonIds;
    private List<ResourceType> resourceTypes;
    private Integer questionCount;
    private List<DifficultyLevel> difficultyLevels;
    private Long teacherId;

    public QuizGenerationRequest() {}

    public List<Long> getChapterIds() { return chapterIds; }
    public void setChapterIds(List<Long> chapterIds) { this.chapterIds = chapterIds; }

    public List<Long> getLessonIds() { return lessonIds; }
    public void setLessonIds(List<Long> lessonIds) { this.lessonIds = lessonIds; }

    public List<ResourceType> getResourceTypes() { return resourceTypes; }
    public void setResourceTypes(List<ResourceType> resourceTypes) { this.resourceTypes = resourceTypes; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }

    public List<DifficultyLevel> getDifficultyLevels() { return difficultyLevels; }
    public void setDifficultyLevels(List<DifficultyLevel> difficultyLevels) { this.difficultyLevels = difficultyLevels; }

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
}
