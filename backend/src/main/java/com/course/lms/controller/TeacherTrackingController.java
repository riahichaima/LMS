package com.course.lms.controller;

import com.course.lms.dto.TrackingDtos.CourseDetail;
import com.course.lms.dto.TrackingDtos.Overview;
import com.course.lms.service.TeacherTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Suivi pédagogique :
 *   GET /api/teacher/tracking                         → indicateurs, cours, évaluations, derniers résultats
 *   GET /api/teacher/tracking/courses/{id}            → progression et notes de chaque étudiant du cours
 *   GET /api/teacher/tracking/courses/{id}/export     → export CSV des résultats
 */
@RestController
@RequestMapping("/api/teacher/tracking")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherTrackingController {

    @Autowired
    private TeacherTrackingService trackingService;

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Overview> overview(Authentication auth) {
        return ResponseEntity.ok(trackingService.overview(auth.getName()));
    }

    @GetMapping("/courses/{courseId}")
    public ResponseEntity<CourseDetail> course(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(trackingService.courseDetail(courseId, auth.getName(), isAdmin(auth)));
    }

    @GetMapping("/courses/{courseId}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long courseId, Authentication auth) {
        byte[] csv = trackingService.exportCsv(courseId, auth.getName(), isAdmin(auth));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(trackingService.exportFileName(courseId), StandardCharsets.UTF_8).build().toString())
                .body(csv);
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
