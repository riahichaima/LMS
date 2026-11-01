package com.course.lms.controller;

import com.course.lms.dto.EnrollmentResponse;
import com.course.lms.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EnrollmentController {

    @Autowired
    private EnrollmentService enrollmentService;

    // ==========================================
    // MODULE ÉTUDIANT - Suivi des cours
    // ==========================================

    @PostMapping("/student/enroll/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> enrollInCourse(@PathVariable Long courseId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(enrollmentService.enrollStudent(courseId, email));
    }

    @GetMapping("/student/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<EnrollmentResponse>> getMyEnrollments() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(enrollmentService.getStudentEnrollments(email));
    }

    @PutMapping("/student/enrollments/{enrollmentId}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> updateProgress(
            @PathVariable Long enrollmentId,
            @RequestParam Integer progressPercentage) {
        return ResponseEntity.ok(enrollmentService.updateProgress(enrollmentId, progressPercentage));
    }

    @PutMapping("/student/enrollments/{enrollmentId}/complete")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> markAsCompleted(@PathVariable Long enrollmentId) {
        return ResponseEntity.ok(enrollmentService.markAsCompleted(enrollmentId));
    }

    @DeleteMapping("/student/enrollments/{enrollmentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<String> unenrollFromCourse(@PathVariable Long enrollmentId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        enrollmentService.unenrollStudent(enrollmentId, email);
        return ResponseEntity.ok("Désinscription réussie");
    }

    // ==========================================
    // MODULE FORMATEUR - Gestion des étudiants
    // ==========================================

    @GetMapping("/teacher/courses/{courseId}/enrollments")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<EnrollmentResponse>> getCourseEnrollments(@PathVariable Long courseId) {
        return ResponseEntity.ok(enrollmentService.getCourseEnrollments(courseId));
    }

    // ==========================================
    // MODULE ADMINISTRATEUR - Statistiques
    // ==========================================

    @GetMapping("/admin/courses/{courseId}/enrollment-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> getEnrollmentCount(@PathVariable Long courseId) {
        return ResponseEntity.ok(enrollmentService.getEnrollmentCount(courseId));
    }

    @DeleteMapping("/admin/enrollments/{enrollmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> removeEnrollment(@PathVariable Long enrollmentId) {
        enrollmentService.removeEnrollment(enrollmentId);
        return ResponseEntity.ok("Inscription supprimée");
    }
}
