package com.course.lms.controller;

import com.course.lms.dto.*;
import com.course.lms.service.LmsAiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ai")
public class LmsAiController {

    private final LmsAiService lmsAiService;

    @Autowired
    private com.course.lms.service.AiRateLimiter aiRateLimiter;

    private static String currentEmail() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Autowired
    public LmsAiController(LmsAiService lmsAiService) {
        this.lmsAiService = lmsAiService;
    }

    /**
     * GET /api/ai/status
     * Modèle IA utilisé par LMS_AI (Azure AI Foundry) et sa disponibilité.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<java.util.Map<String, Object>>> status() {
        return lmsAiService.getStatus().map(ResponseEntity::ok);
    }

    /**
     * POST /api/ai/text/upload
     * Envoyer un PDF, TXT ou autre document vers LMS_AI.
     */
    @PostMapping(value = "/text/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public Mono<ResponseEntity<LmsAiContentUploadResponse>> uploadText(
            @RequestParam("file") MultipartFile file) {
        aiRateLimiter.check(currentEmail(), com.course.lms.service.AiRateLimiter.Usage.GENERATION);
        return lmsAiService.uploadTextFile(file)
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/ai/text/ask
     * Envoyer une question et récupérer la réponse du Text Agent.
     */
    @PostMapping("/text/ask")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public Mono<ResponseEntity<LmsAiQuestionResponse>> askText(
            @Valid @RequestBody LmsAiQuestionRequest request) {
        aiRateLimiter.check(currentEmail(), com.course.lms.service.AiRateLimiter.Usage.CHAT);
        return lmsAiService.askTextAgent(request)
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/ai/video/upload
     * Envoyer une vidéo locale ou une URL YouTube vers LMS_AI.
     */
    @PostMapping(value = "/video/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public Mono<ResponseEntity<Object>> uploadVideo(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "title", required = false) String title) {
        aiRateLimiter.check(currentEmail(), com.course.lms.service.AiRateLimiter.Usage.GENERATION);

        if (file != null && !file.isEmpty()) {
            return lmsAiService.uploadVideoFile(file)
                    .map(resp -> ResponseEntity.ok((Object) resp));
        } else if (url != null && !url.trim().isEmpty()) {
            LmsAiUrlIngestRequest request = LmsAiUrlIngestRequest.builder()
                    .url(url)
                    .title(title)
                    .build();
            return lmsAiService.ingestVideoUrl(request)
                    .map(resp -> ResponseEntity.ok((Object) resp));
        } else {
            return Mono.just(ResponseEntity.badRequest()
                    .body("Vous devez fournir soit un fichier local ('file'), soit une URL ('url')."));
        }
    }

    /**
     * POST /api/ai/video/ask
     * Poser une question sur une vidéo déjà indexée.
     */
    @PostMapping("/video/ask")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public Mono<ResponseEntity<LmsAiQuestionResponse>> askVideo(
            @Valid @RequestBody LmsAiQuestionRequest request) {
        aiRateLimiter.check(currentEmail(), com.course.lms.service.AiRateLimiter.Usage.CHAT);
        return lmsAiService.askVideoAgent(request)
                .map(ResponseEntity::ok);
    }
}
