package com.course.lms.dto;

import com.course.lms.model.DifficultyLevel;
import java.util.List;

public class AssessmentGenerationRequest {
    private List<Long> chapterIds;
    private Boolean generateQuiz;
    private Boolean generateAssignment;
    private Integer quizQuestionCount;
    private Integer assignmentExerciseCount;
    private List<DifficultyLevel> difficultyLevels;
    private Long teacherId;
    // Types de ressources à utiliser (PDF, VIDEO, AUDIO, YOUTUBE_VIDEO, WEB_PAGE, TEXT). Vide = toutes.
    private List<String> resourceTypes;

    // Rendu : date limite commune et temps de travail (minutes) des quiz / TP générés. Null = sans limite.
    private java.time.Instant dueAt;
    private Integer quizDurationMinutes;
    private Integer assignmentDurationMinutes;

    public java.time.Instant getDueAt() { return dueAt; }
    public void setDueAt(java.time.Instant dueAt) { this.dueAt = dueAt; }
    public Integer getQuizDurationMinutes() { return quizDurationMinutes; }
    public void setQuizDurationMinutes(Integer quizDurationMinutes) { this.quizDurationMinutes = quizDurationMinutes; }
    public Integer getAssignmentDurationMinutes() { return assignmentDurationMinutes; }
    public void setAssignmentDurationMinutes(Integer assignmentDurationMinutes) { this.assignmentDurationMinutes = assignmentDurationMinutes; }

    public AssessmentGenerationRequest() {}

    public List<String> getResourceTypes() { return resourceTypes; }
    public void setResourceTypes(List<String> resourceTypes) { this.resourceTypes = resourceTypes; }

    public List<Long> getChapterIds() { return chapterIds; }
    public void setChapterIds(List<Long> chapterIds) { this.chapterIds = chapterIds; }

    public Boolean getGenerateQuiz() { return generateQuiz; }
    public void setGenerateQuiz(Boolean generateQuiz) { this.generateQuiz = generateQuiz; }

    public Boolean getGenerateAssignment() { return generateAssignment; }
    public void setGenerateAssignment(Boolean generateAssignment) { this.generateAssignment = generateAssignment; }

    public Integer getQuizQuestionCount() { return quizQuestionCount; }
    public void setQuizQuestionCount(Integer quizQuestionCount) { this.quizQuestionCount = quizQuestionCount; }

    public Integer getAssignmentExerciseCount() { return assignmentExerciseCount; }
    public void setAssignmentExerciseCount(Integer assignmentExerciseCount) { this.assignmentExerciseCount = assignmentExerciseCount; }

    public List<DifficultyLevel> getDifficultyLevels() { return difficultyLevels; }
    public void setDifficultyLevels(List<DifficultyLevel> difficultyLevels) { this.difficultyLevels = difficultyLevels; }

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
}
