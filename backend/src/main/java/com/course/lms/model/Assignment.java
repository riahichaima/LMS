package com.course.lms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assignments")
public class Assignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    // Solution générée par l'IA, utilisée pour la correction — jamais envoyée à l'étudiant
    @JsonIgnore
    @Column(name = "expected_solution", columnDefinition = "LONGTEXT")
    private String expectedSolution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficultyLevel;
    
    @Column(nullable = false)
    private Integer exerciseCount;
    
    @Column(name = "time_limit_hours")
    private Integer timeLimitHours;
    
    @Column(nullable = false)
    private Double maxPoints;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "generated_by_ai", nullable = false)
    private Boolean generatedByAi = false;

    // Date limite de rendu fixée par l'enseignant (null = sans date limite)
    @Column(name = "due_at")
    private java.time.Instant dueAt;

    // Temps de travail une fois commencé, en minutes (null = non minuté)
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Submission> submissions = new java.util.ArrayList<>();
    
    // Constructors
    public Assignment() {}
    
    public Assignment(Lesson lesson, String title, DifficultyLevel difficultyLevel, Integer exerciseCount, Integer timeLimitHours, Double maxPoints) {
        this.lesson = lesson;
        this.title = title;
        this.difficultyLevel = difficultyLevel;
        this.exerciseCount = exerciseCount;
        this.timeLimitHours = timeLimitHours;
        this.maxPoints = maxPoints;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Lesson getLesson() { return lesson; }
    public void setLesson(Lesson lesson) { this.lesson = lesson; }

    public Chapter getChapter() { return chapter; }
    public void setChapter(Chapter chapter) { this.chapter = chapter; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExpectedSolution() { return expectedSolution; }
    public void setExpectedSolution(String expectedSolution) { this.expectedSolution = expectedSolution; }
    
    public DifficultyLevel getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(DifficultyLevel difficultyLevel) { this.difficultyLevel = difficultyLevel; }
    
    public Integer getExerciseCount() { return exerciseCount; }
    public void setExerciseCount(Integer exerciseCount) { this.exerciseCount = exerciseCount; }
    
    public Integer getTimeLimitHours() { return timeLimitHours; }
    public void setTimeLimitHours(Integer timeLimitHours) { this.timeLimitHours = timeLimitHours; }
    
    public Double getMaxPoints() { return maxPoints; }
    public void setMaxPoints(Double maxPoints) { this.maxPoints = maxPoints; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public Boolean getGeneratedByAi() { return generatedByAi; }
    public void setGeneratedByAi(Boolean generatedByAi) { this.generatedByAi = generatedByAi; }

    public java.time.Instant getDueAt() { return dueAt; }
    public void setDueAt(java.time.Instant dueAt) { this.dueAt = dueAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    
    public List<Submission> getSubmissions() { return submissions; }
    public void setSubmissions(List<Submission> submissions) { this.submissions = submissions; }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
