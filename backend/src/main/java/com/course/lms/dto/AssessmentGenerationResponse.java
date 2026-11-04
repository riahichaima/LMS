package com.course.lms.dto;

import java.util.List;

public class AssessmentGenerationResponse {
    private Boolean success;
    private String message;
    private List<GeneratedQuiz> generatedQuizzes;
    private List<GeneratedAssignment> generatedAssignments;

    public static class GeneratedQuiz {
        private Long quizId;
        private String title;
        private String difficultyLevel;
        private Integer questionCount;

        public Long getQuizId() { return quizId; }
        public void setQuizId(Long quizId) { this.quizId = quizId; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

        public Integer getQuestionCount() { return questionCount; }
        public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    }

    public static class GeneratedAssignment {
        private Long assignmentId;
        private String title;
        private String difficultyLevel;
        private Integer exerciseCount;

        public Long getAssignmentId() { return assignmentId; }
        public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

        public Integer getExerciseCount() { return exerciseCount; }
        public void setExerciseCount(Integer exerciseCount) { this.exerciseCount = exerciseCount; }
    }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<GeneratedQuiz> getGeneratedQuizzes() { return generatedQuizzes; }
    public void setGeneratedQuizzes(List<GeneratedQuiz> generatedQuizzes) { this.generatedQuizzes = generatedQuizzes; }

    public List<GeneratedAssignment> getGeneratedAssignments() { return generatedAssignments; }
    public void setGeneratedAssignments(List<GeneratedAssignment> generatedAssignments) { this.generatedAssignments = generatedAssignments; }
}
