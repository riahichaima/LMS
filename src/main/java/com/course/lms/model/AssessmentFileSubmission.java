package com.course.lms.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Copie rendue par un étudiant pour un quiz / TP importé ; notée sur 20 par l'enseignant. */
@Entity
@Table(name = "assessment_file_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_assessment_file_student", columnNames = {"assessment_file_id", "student_id"}))
public class AssessmentFileSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_file_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AssessmentFile assessmentFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User student;

    @Column(name = "file_name", nullable = false, length = 80)
    private String fileName;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /** Note sur 20 (null tant que l'enseignant n'a pas corrigé) */
    private Double grade;

    @Column(length = 2000)
    private String feedback;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    public Long getId() { return id; }
    public AssessmentFile getAssessmentFile() { return assessmentFile; }
    public void setAssessmentFile(AssessmentFile assessmentFile) { this.assessmentFile = assessmentFile; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Double getGrade() { return grade; }
    public void setGrade(Double grade) { this.grade = grade; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
}
