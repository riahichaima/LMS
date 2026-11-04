package com.course.lms.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Avancement réel d'un étudiant sur une ressource : pages lues d'un PDF, part regardée d'une vidéo
 * ou écoutée d'un audio (0 à 100 %, ne redescend jamais).
 */
@Entity
@Table(name = "resource_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_progress", columnNames = {"student_id", "resource_id"}),
        indexes = @Index(name = "idx_resource_progress_course", columnList = "student_id, course_id"))
public class ResourceProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LessonResource resource;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Integer percent = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ResourceProgress() {}

    public ResourceProgress(User student, LessonResource resource, Long lessonId, Long courseId) {
        this.student = student;
        this.resource = resource;
        this.lessonId = lessonId;
        this.courseId = courseId;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public LessonResource getResource() { return resource; }
    public Long getLessonId() { return lessonId; }
    public Long getCourseId() { return courseId; }
    public Integer getPercent() { return percent; }
    public void setPercent(Integer percent) { this.percent = percent; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
