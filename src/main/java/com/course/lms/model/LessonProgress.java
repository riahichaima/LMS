package com.course.lms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Leçon terminée par un étudiant : sert à calculer la progression du cours.
 */
@Entity
@Table(name = "lesson_progress", uniqueConstraints = @UniqueConstraint(
        name = "uk_lesson_progress_student_lesson", columnNames = {"student_id", "lesson_id"}))
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    public LessonProgress() {}

    public LessonProgress(User student, Lesson lesson, Long courseId) {
        this.student = student;
        this.lesson = lesson;
        this.courseId = courseId;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public Lesson getLesson() { return lesson; }
    public Long getCourseId() { return courseId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
