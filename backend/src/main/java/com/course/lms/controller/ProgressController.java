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

    /**
     * Analyze student performance and get recommendations
     * Student and Teacher endpoint
     */
    @GetMapping("/analyze/{studentId}/course/{courseId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public CompletableFuture<ResponseEntity<StudentRecommendationsResponse>> analyzeProgress(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        aiRateLimiter.check(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName(), com.course.lms.service.AiRateLimiter.Usage.CHAT);
        return progressAnalysisService.analyzeStudentPerformanceAsync(studentId, courseId)
                .thenApply(ResponseEntity::ok);
    }
}
