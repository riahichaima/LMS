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
