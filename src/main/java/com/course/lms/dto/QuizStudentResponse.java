package com.course.lms.dto;

import java.util.List;

public class QuizStudentResponse {
    private Long id;
    private String title;
    private String description;
    private String difficultyLevel;
    private Integer questionCount;
    private Integer timeLimitMinutes;
    private Double passingScore;
    private List<QuestionWithoutAnswer> questions;

    public QuizStudentResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }

    public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(Integer timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }

    public Double getPassingScore() { return passingScore; }
    public void setPassingScore(Double passingScore) { this.passingScore = passingScore; }

    public List<QuestionWithoutAnswer> getQuestions() { return questions; }
    public void setQuestions(List<QuestionWithoutAnswer> questions) { this.questions = questions; }

    public static class QuestionWithoutAnswer {
        private Long id;
        private String questionText;
        private String questionType;
        private Integer points;
        private Integer questionOrder;
        private List<AnswerChoice> answers;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }

        public Integer getPoints() { return points; }
        public void setPoints(Integer points) { this.points = points; }

        public Integer getQuestionOrder() { return questionOrder; }
        public void setQuestionOrder(Integer questionOrder) { this.questionOrder = questionOrder; }

        public List<AnswerChoice> getAnswers() { return answers; }
        public void setAnswers(List<AnswerChoice> answers) { this.answers = answers; }
    }

    public static class AnswerChoice {
        private Long id;
        private String answerText;
        private Integer answerOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getAnswerText() { return answerText; }
        public void setAnswerText(String answerText) { this.answerText = answerText; }

        public Integer getAnswerOrder() { return answerOrder; }
        public void setAnswerOrder(Integer answerOrder) { this.answerOrder = answerOrder; }
    }
}
