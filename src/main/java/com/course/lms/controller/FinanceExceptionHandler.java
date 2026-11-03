package com.course.lms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// Messages d'erreur lisibles pour les API de paiement, de finance et de contenu protégé : { "message": "..." }
@RestControllerAdvice(assignableTypes = {PaymentController.class, TeacherEarningsController.class,
        AdminFinanceController.class, SuperAdminFinanceController.class, ChapterController.class,
        LessonController.class, AssessmentController.class, CourseController.class, EnrollmentController.class,
        StudentLearningController.class, MessageController.class, NotificationController.class,
        TeacherTrackingController.class, SupervisionController.class, FeedbackController.class, ProfileController.class, MeetingController.class})
public class FinanceExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // Accès refusé (cours payant non acheté, paiement d'un autre étudiant...)
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }
}
