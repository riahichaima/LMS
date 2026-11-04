package com.course.lms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "answers")
public class Answer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String answerText;
    
    @Column(nullable = false)
    private Boolean isCorrect;
    
    @Column(name = "answer_order")
    private Integer answerOrder;
    
    // Constructors
    public Answer() {}
    
    public Answer(Question question, String answerText, Boolean isCorrect) {
        this.question = question;
        this.answerText = answerText;
        this.isCorrect = isCorrect;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    
    public Integer getAnswerOrder() { return answerOrder; }
    public void setAnswerOrder(Integer answerOrder) { this.answerOrder = answerOrder; }
}
