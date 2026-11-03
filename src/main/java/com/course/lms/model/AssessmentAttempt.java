package com.course.lms.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Passage minuté d'un quiz ou d'un TP par un étudiant : l'heure de début et l'heure de fin sont fixées
 * par le serveur (recharger la page ne relance pas le chrono). Les réponses en cours sont gardées
 * dans draft et envoyées automatiquement quand le temps est écoulé.
 */
@Entity
@Table(name = "assessment_attempts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "kind", "assessment_id"}))
public class AssessmentAttempt {

    public static final String QUIZ = "QUIZ";
    public static final String TP = "TP";
    public static final String FILE = "FILE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User student;

    // QUIZ (quiz IA), TP (TP IA) ou FILE (sujet importé en fichier)
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(columnDefinition = "LONGTEXT")
    private String draft;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // Envoyé par le serveur à la fin du temps (l'étudiant n'avait pas validé)
    @Column(name = "auto_submitted", nullable = false)
    private Boolean autoSubmitted = false;

    public AssessmentAttempt() {}

    public AssessmentAttempt(User student, String kind, Long assessmentId, Instant startedAt, Instant endsAt) {
        this.student = student;
        this.kind = kind;
        this.assessmentId = assessmentId;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public String getKind() { return kind; }
    public Long getAssessmentId() { return assessmentId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Boolean getAutoSubmitted() { return autoSubmitted; }
    public void setAutoSubmitted(Boolean autoSubmitted) { this.autoSubmitted = autoSubmitted; }
}
