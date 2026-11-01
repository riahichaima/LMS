package com.course.lms.controller;

import com.course.lms.service.StudentLearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Espace étudiant : lire un cours, suivre sa progression, faire les TP, voir ses statistiques
 * et discuter avec le chatbot IA du cours.
 */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentLearningController {

    @Autowired
    private StudentLearningService learningService;

    private static String currentEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** Contenu du cours (PDF, vidéos, liens), leçons terminées, quiz et TP par chapitre. */
    @GetMapping("/courses/{courseId}/learn")
    public ResponseEntity<Map<String, Object>> learn(@PathVariable Long courseId) {
        return ResponseEntity.ok(learningService.getLearningView(courseId, currentEmail()));
    }

    @PostMapping("/lessons/{lessonId}/complete")
    public ResponseEntity<Map<String, Object>> completeLesson(@PathVariable Long lessonId) {
        return ResponseEntity.ok(learningService.completeLesson(lessonId, currentEmail()));
    }

    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<Map<String, Object>> assignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(learningService.getAssignment(assignmentId, currentEmail()));
    }

    /** Body : { "answer": "..." } — renvoie la note, le feedback et la correction. */
    @PostMapping("/assignments/{assignmentId}/submit")
    public ResponseEntity<Map<String, Object>> submitAssignment(@PathVariable Long assignmentId,
                                                                @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(learningService.submitAssignment(assignmentId, body.get("answer"), currentEmail()));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics() {
        return ResponseEntity.ok(learningService.getStatistics(currentEmail()));
    }

    /** Body : { "messages": [{role, content}], "action": "chat" | "summary", "lessonId": 12 } */
    @PostMapping("/courses/{courseId}/chat")
    public ResponseEntity<Map<String, Object>> chat(@PathVariable Long courseId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(learningService.courseChat(courseId, body, currentEmail()));
    }
}
