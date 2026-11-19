package com.course.lms.controller;

import com.course.lms.service.AiRateLimiter;
import com.course.lms.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Recherche dans tous les cours (RAG), réservée à l'administrateur et au super administrateur.
 * Un enseignant et un étudiant gardent l'assistant de leur cours (contenu complet, sans RAG).
 */
@RestController
@RequestMapping("/api/ai/rag")
@PreAuthorize("hasRole('ADMIN')")
public class RagController {

    private final RagService ragService;
    private final AiRateLimiter aiRateLimiter;

    public RagController(RagService ragService, AiRateLimiter aiRateLimiter) {
        this.ragService = ragService;
        this.aiRateLimiter = aiRateLimiter;
    }

    /** Reconstruire l'index à partir des leçons de tous les cours. */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex(Authentication auth,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean complet) {
        aiRateLimiter.check(auth.getName(), AiRateLimiter.Usage.GENERATION);
        return ResponseEntity.ok(ragService.rebuildIndex(complet));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(ragService.status());
    }

    /** Question de l'administration : réponse rédigée à partir des passages retrouvés, avec les sources. */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, String> body, Authentication auth) {
        aiRateLimiter.check(auth.getName(), AiRateLimiter.Usage.CHAT);
        return ResponseEntity.ok(ragService.ask(body.get("question")));
    }
}
