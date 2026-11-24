package com.course.lms.controller;

import com.course.lms.dto.StudentRecommendationsResponse;
import com.course.lms.service.ProgressAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/progress")
@CrossOrigin(origins = "*")
public class ProgressController {

    @Autowired
    private ProgressAnalysisService progressAnalysisService;

    @Autowired
    private com.course.lms.service.AiRateLimiter aiRateLimiter;

    @Autowired
    private com.course.lms.service.CourseAccessService courseAccessService;

    /**
     * Analyze student performance and get recommendations
     * Student and Teacher endpoint
     */
    @GetMapping("/analyze/{studentId}/course/{courseId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public CompletableFuture<ResponseEntity<StudentRecommendationsResponse>> analyzeProgress(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean student = auth.getAuthorities().stream().anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()));
        if (student) {
            com.course.lms.model.User me = courseAccessService.currentUser();
            if (me == null || !me.getId().equals(studentId)) {
                throw new SecurityException("Un étudiant ne peut analyser que sa propre progression.");
            }
        } else {
            courseAccessService.requireManageCourse(courseId);
        }
        aiRateLimiter.check(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName(), com.course.lms.service.AiRateLimiter.Usage.CHAT);
        return progressAnalysisService.analyzeStudentPerformanceAsync(studentId, courseId)
                .thenApply(ResponseEntity::ok);
    }

    /** Administration : progression de tous les étudiants d'un cours, analysée par l'agent de recommandation. */
    @GetMapping("/analyze/course/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<StudentRecommendationsResponse>> analyzeCourse(@PathVariable Long courseId) {
        aiRateLimiter.check(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName(), com.course.lms.service.AiRateLimiter.Usage.CHAT);
        return progressAnalysisService.analyzeCourseAsync(courseId).thenApply(ResponseEntity::ok);
    }
}
