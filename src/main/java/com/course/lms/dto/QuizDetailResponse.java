package com.course.lms.dto;

import java.util.List;

public class QuizDetailResponse {
    private Long id;
    private String title;
    private String description;
    private String difficultyLevel;
    private Integer questionCount;
    private Integer timeLimitMinutes;
    private Double passingScore;
    private String lessonTitle;
    private String chapterTitle;
    private List<QuestionDetail> questions;

    public QuizDetailResponse() {}

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

    public String getLessonTitle() { return lessonTitle; }
    public void setLessonTitle(String lessonTitle) { this.lessonTitle = lessonTitle; }

    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }

    public List<QuestionDetail> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDetail> questions) { this.questions = questions; }

    public static class QuestionDetail {
        private Long id;
        private String questionText;
        private String questionType;
        private String correctAnswer;
        private String explanation;
        private Integer points;
        private Integer questionOrder;
        private List<AnswerDetail> answers;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }

        public String getCorrectAnswer() { return correctAnswer; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }

        public Integer getPoints() { return points; }
        public void setPoints(Integer points) { this.points = points; }

        public Integer getQuestionOrder() { return questionOrder; }
        public void setQuestionOrder(Integer questionOrder) { this.questionOrder = questionOrder; }

        public List<AnswerDetail> getAnswers() { return answers; }
        public void setAnswers(List<AnswerDetail> answers) { this.answers = answers; }
    }

    public static class AnswerDetail {
        private Long id;
        private String answerText;
        private Boolean isCorrect;
        private Integer answerOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getAnswerText() { return answerText; }
        public void setAnswerText(String answerText) { this.answerText = answerText; }

        public Boolean getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }

        public Integer getAnswerOrder() { return answerOrder; }
        public void setAnswerOrder(Integer answerOrder) { this.answerOrder = answerOrder; }
    }
}
