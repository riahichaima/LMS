package com.course.lms.controller;

import com.course.lms.dto.ChapterRequest;
import com.course.lms.dto.ChapterResponse;
import com.course.lms.service.ChapterService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    // ESPACE PROFESSEUR
    @PostMapping("/teacher/chapters")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ChapterResponse> createChapter(@Valid @RequestBody ChapterRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(chapterService.createChapter(request, auth.getName(), isAdmin));
    }

    // ESPACE PUBLIC / ETUDIANT
    @GetMapping("/courses/{courseId}/chapters")
    public ResponseEntity<List<ChapterResponse>> getChaptersByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseAccessService.protectChaptersOfCourse(courseId, chapterService.getChaptersByCourseId(courseId)));
    }

    // Voir un chapitre spécifique
    @GetMapping("/chapters/{chapterId}")
    public ResponseEntity<ChapterResponse> getChapterById(@PathVariable Long chapterId) {
        return ResponseEntity.ok(courseAccessService.protectChapter(chapterId, chapterService.getChapterById(chapterId)));
    }

    @Autowired
    private com.course.lms.service.CourseAccessService courseAccessService;

    @GetMapping("/teacher/chapters/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ChapterResponse> getTeacherChapterById(@PathVariable Long id) {
        return ResponseEntity.ok(chapterService.getChapterById(id));
    }

    @PutMapping("/teacher/chapters/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ChapterResponse> updateChapter(@PathVariable Long id, @Valid @RequestBody ChapterRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(chapterService.updateChapter(id, request, auth.getName(), isAdmin));
    }

    @DeleteMapping("/teacher/chapters/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteChapter(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        chapterService.deleteChapter(id, auth.getName(), isAdmin);
        return ResponseEntity.ok("Chapitre supprimé avec succès");
    }
}
