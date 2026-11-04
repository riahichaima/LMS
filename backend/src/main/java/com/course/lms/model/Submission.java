package com.course.lms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column
    private Double points;
    
    @Column
    private Double maxPoints;
    
    @Column(columnDefinition = "TEXT")
    private String feedback;
    
    @Column(columnDefinition = "TEXT")
    private String detailedExplanation;
    
    @Column(name = "graded_by_ai")
    private Boolean gradedByAi = false;
    
    @Column(name = "graded_at")
    private LocalDateTime gradedAt;
    
    // Constructors
    public Submission() {}
    
    public Submission(Assignment assignment, User student, String content) {
        this.assignment = assignment;
        this.student = student;
        this.content = content;
        this.submittedAt = LocalDateTime.now();
    }

    public Submission(Quiz quiz, User student, String content) {
        this.quiz = quiz;
        this.student = student;
        this.content = content;
        this.submittedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }

    public Quiz getQuiz() { return quiz; }
    public void setQuiz(Quiz quiz) { this.quiz = quiz; }
    
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    
    public Double getPoints() { return points; }
    public void setPoints(Double points) { this.points = points; }
    
    public Double getMaxPoints() { return maxPoints; }
    public void setMaxPoints(Double maxPoints) { this.maxPoints = maxPoints; }
    
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    
    public String getDetailedExplanation() { return detailedExplanation; }
    public void setDetailedExplanation(String detailedExplanation) { this.detailedExplanation = detailedExplanation; }
    
    public Boolean getGradedByAi() { return gradedByAi; }
    public void setGradedByAi(Boolean gradedByAi) { this.gradedByAi = gradedByAi; }
    
    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
    
    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }
}
