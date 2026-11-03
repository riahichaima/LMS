package com.course.lms.controller;

import com.course.lms.service.ManualAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Suppression par l'enseignant des quiz et TP générés par l'IA pour ses cours. */
@RestController
@RequestMapping("/api/assessments/manual")
@PreAuthorize("hasRole('TEACHER')")
public class ManualAssessmentController {

    private final ManualAssessmentService service;

    public ManualAssessmentController(ManualAssessmentService service) {
        this.service = service;
    }

    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long id, Authentication auth) {
        service.deleteQuiz(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id, Authentication auth) {
        service.deleteAssignment(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
