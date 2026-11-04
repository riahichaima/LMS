package com.course.lms.controller;

import com.course.lms.dto.CourseRequest;
import com.course.lms.dto.CourseResponse;
import com.course.lms.dto.ChapterRequest;
import com.course.lms.dto.LessonRequest;
import com.course.lms.dto.LessonResourceRequest;
import com.course.lms.service.CourseService;
import com.course.lms.service.FileStorageService;
import com.course.lms.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private com.course.lms.service.CourseAccessService courseAccessService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ==========================================
    // ESPACE PROFESSEUR (TEACHER / ADMIN)
    // ==========================================

    @PostMapping("/teacher/courses")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(courseService.createCourse(request, email));
    }

    @PostMapping(value = "/teacher/courses/full", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> createFullCourse(
            @RequestParam("course") String courseParam,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        
        CourseRequest request;
        try {
            request = objectMapper.readValue(courseParam, CourseRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de lecture du JSON course: " + e.getMessage());
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        if (files != null && !files.isEmpty() && request.getChapters() != null) {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null) continue;

                // Traverse the Course Request structure to find matching resource requests
                for (ChapterRequest chapReq : request.getChapters()) {
                    if (chapReq.getLessons() != null) {
                        for (LessonRequest lesReq : chapReq.getLessons()) {
                            if (lesReq.getResources() != null) {
                                for (LessonResourceRequest resReq : lesReq.getResources()) {
                                    if (originalFilename.equals(resReq.getFileName())) {
                                        try {
                                            String filePath = fileStorageService.storePhysicalFileOnly(file);
                                            resReq.setFilePath(filePath);
                                            resReq.setMimeType(file.getContentType());
                                            resReq.setSize(file.getSize());
                                        } catch (Exception e) {
                                            throw new RuntimeException("Erreur lors de l'upload du fichier " + originalFilename + ": " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return ResponseEntity.ok(courseService.createFullCourse(request, email));
    }

    @GetMapping("/teacher/courses")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<CourseResponse>> getTeacherCourses() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(courseService.getMyCourses(email));
    }

    @GetMapping("/teacher/courses/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> getTeacherCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PutMapping("/teacher/courses/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(courseService.updateCourse(id, request, auth.getName(), isAdmin));
    }

    @PutMapping(value = "/teacher/courses/{id}/full", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> updateFullCourse(
            @PathVariable Long id,
            @RequestParam("course") String courseParam,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        CourseRequest request;
        try {
            request = objectMapper.readValue(courseParam, CourseRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de lecture du JSON course: " + e.getMessage());
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (files != null && !files.isEmpty() && request.getChapters() != null) {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null) continue;
                for (ChapterRequest chapReq : request.getChapters()) {
                    if (chapReq.getLessons() != null) {
                        for (LessonRequest lesReq : chapReq.getLessons()) {
                            if (lesReq.getResources() != null) {
                                for (LessonResourceRequest resReq : lesReq.getResources()) {
                                    if (originalFilename.equals(resReq.getFileName())) {
                                        try {
                                            String filePath = fileStorageService.storePhysicalFileOnly(file);
                                            resReq.setFilePath(filePath);
                                            resReq.setMimeType(file.getContentType());
                                            resReq.setSize(file.getSize());
                                        } catch (Exception e) {
                                            throw new RuntimeException("Erreur upload fichier: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return ResponseEntity.ok(courseService.updateFullCourse(id, request, email, isAdmin));
    }

    @DeleteMapping("/teacher/courses/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteCourse(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        courseService.deleteCourse(id, auth.getName(), isAdmin);
        return ResponseEntity.ok("Cours supprimé avec succès");
    }

    @PutMapping("/admin/formations/{courseId}/assign-trainer/{teacherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> assignTrainerToFormation(@PathVariable Long courseId, @PathVariable Long teacherId) {
        return ResponseEntity.ok(courseService.assignTeacherToCourse(courseId, teacherId));
    }

    // ==========================================
    // ESPACE PUBLIC / ETUDIANT
    // ==========================================

    @GetMapping("/courses")
    public ResponseEntity<Page<CourseResponse>> getAllCourses(
            @RequestParam(required = false) String title,
            Pageable pageable) {
        if (title != null && !title.isEmpty()) {
            return ResponseEntity.ok(courseService.searchCourses(title, pageable).map(courseAccessService::protect));
        }
        return ResponseEntity.ok(courseService.getAllCourses(pageable).map(courseAccessService::protect));
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseAccessService.protect(courseService.getCourseById(id)));
    }

    @GetMapping("/student/courses")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Page<CourseResponse>> getStudentCourses(Pageable pageable) {
        return ResponseEntity.ok(courseService.getAllCourses(pageable).map(courseAccessService::protect));
    }
}
