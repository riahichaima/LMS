package com.course.lms.dto;

import java.time.LocalDateTime;

/** Quiz / TP importés en fichier, copies des étudiants et notes. */
public final class AssessmentFileDtos {

    private AssessmentFileDtos() {}

    public record SubmissionView(Long id, Long studentId, String studentName, String studentEmail, String originalName,
                                 Long sizeBytes, LocalDateTime submittedAt, Double grade, String feedback, LocalDateTime gradedAt) {}

    /** Vue enseignant (avec le nombre de copies) ou étudiant (avec sa propre copie dans mySubmission). */
    public record AssessmentFileView(Long id, String type, String title, String instructions, String difficultyLevel,
                                     Long courseId, String courseTitle, Long chapterId, String chapterTitle,
                                     String originalName, String mimeType, Long sizeBytes, LocalDateTime createdAt,
                                     long submissionCount, long gradedCount, SubmissionView mySubmission,
                                     java.time.Instant dueAt, Integer durationMinutes, java.util.Map<String, Object> schedule) {}

    public record GradeRequest(Double grade, String feedback) {}

    /** Fichier à renvoyer au navigateur (chemin, nom d'origine, type). */
    public record Download(java.nio.file.Path path, String originalName, String mimeType) {}
}
