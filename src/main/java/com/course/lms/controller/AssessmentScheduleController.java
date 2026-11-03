package com.course.lms.controller;

import com.course.lms.service.AssessmentScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Dates limites et chrono des quiz / TP.
 * kind = QUIZ (quiz IA), TP (TP IA) ou FILE (sujet importé en fichier).
 */
@RestController
public class AssessmentScheduleController {

    public record ScheduleRequest(Instant dueAt, Integer durationMinutes) {}

    public record DraftRequest(String draft) {}

    private final AssessmentScheduleService service;

    public AssessmentScheduleController(AssessmentScheduleService service) {
        this.service = service;
    }

    /** Enseignant : date limite de rendu et temps de travail (null = sans limite). */
    @PutMapping("/api/assessment-schedule/{kind}/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public Map<String, Object> update(@PathVariable String kind, @PathVariable Long id,
                                      @RequestBody ScheduleRequest request, Authentication auth) {
        return service.update(auth.getName(), kind, id, request.dueAt(), request.durationMinutes());
    }

    /** Étudiant : règles et état de son passage (sans démarrer le chrono). */
    @GetMapping("/api/student/attempts/{kind}/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> status(@PathVariable String kind, @PathVariable Long id, Authentication auth) {
        return service.status(auth.getName(), kind, id);
    }

    /** Étudiant : « Commencer » (démarre le chrono une seule fois). */
    @PostMapping("/api/student/attempts/{kind}/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> start(@PathVariable String kind, @PathVariable Long id, Authentication auth) {
        return service.start(auth.getName(), kind, id);
    }

    /** Étudiant : brouillon des réponses (envoyé automatiquement à la fin du temps). */
    @PutMapping("/api/student/attempts/{kind}/{id}/draft")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> draft(@PathVariable String kind, @PathVariable Long id,
                                     @RequestBody DraftRequest request, Authentication auth) {
        return service.saveDraft(auth.getName(), kind, id, request.draft());
    }
}
