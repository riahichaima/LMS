package com.course.lms.controller;

import com.course.lms.dto.CourseRequest;
import com.course.lms.dto.CourseResponse;
import com.course.lms.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseController {

    @Autowired
    private CourseService courseService;

    // ==========================================
    // ESPACE PROFESSEUR (TEACHER / ADMIN)
    // ==========================================

    @PostMapping("/teacher/courses")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(courseService.createCourse(request, email));
    }

    @GetMapping("/teacher/courses")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<CourseResponse>> getTeacherCourses() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(courseService.getMyCourses(email));
    }

    @PutMapping("/teacher/courses/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(courseService.updateCourse(id, request, auth.getName(), isAdmin));
    }

    @DeleteMapping("/teacher/courses/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteCourse(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        courseService.deleteCourse(id, auth.getName(), isAdmin);
        return ResponseEntity.ok("Cours supprimé avec succès");
    }

    // ==========================================
    // ESPACE PUBLIC / ETUDIANT
    // ==========================================

    @GetMapping("/courses")
    public ResponseEntity<Page<CourseResponse>> getAllCourses(
            @RequestParam(required = false) String title,
            Pageable pageable) {
        if (title != null && !title.isEmpty()) {
            return ResponseEntity.ok(courseService.searchCourses(title, pageable));
        }
        return ResponseEntity.ok(courseService.getAllCourses(pageable));
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }
}
