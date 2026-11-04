package com.course.lms.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Suivi pédagogique (formateur) : progression des étudiants, notes et participation. */
public final class TrackingDtos {

    private TrackingDtos() {}

    /** Indicateurs agrégés (tous les cours du formateur, ou un cours). */
    public record Kpis(int courses, long students, long enrollments, int averageProgress, int completionRate,
                       long completed, Double quizAverage, Double assignmentAverage, long submissions,
                       int participationRate, long activeLast7Days, Double passRate) {}

    public record CourseSummary(Long courseId, String title, String pricingType, long students, int averageProgress,
                                long completed, Double quizAverage, Double assignmentAverage, long submissions,
                                int participationRate, int assessments) {}

    /** Statistiques d'un quiz ou d'un TP. */
    public record AssessmentStats(Long id, String type, String title, Long courseId, String courseTitle,
                                  long attempts, long students, Double averageScore, Double bestScore,
                                  Double passRate, Double passingScore) {}

    public record RecentSubmission(String type, String title, String courseTitle, String studentName,
                                   Double percent, LocalDateTime submittedAt) {}

    public record Overview(Kpis kpis, List<CourseSummary> courses, List<AssessmentStats> assessments,
                           List<RecentSubmission> recentSubmissions) {}

    /** Ligne étudiant d'un cours : meilleur score par évaluation. */
    public record StudentRow(Long studentId, String name, String email, LocalDateTime enrolledAt,
                             LocalDateTime lastAccessedAt, int progress, long lessonsCompleted, int totalLessons,
                             boolean completed, long quizzesTaken, Double quizAverage, long assignmentsSubmitted,
                             Double assignmentAverage, boolean active) {}

    public record CourseDetail(Long courseId, String title, int totalLessons, Kpis kpis, List<StudentRow> students,
                               List<AssessmentStats> assessments) {}
}
