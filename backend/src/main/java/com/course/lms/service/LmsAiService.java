package com.course.lms.service;

import com.course.lms.dto.*;
import com.course.lms.exception.LmsAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class LmsAiService {

    private final WebClient webClient;

    @Autowired
    public LmsAiService(@Qualifier("lmsAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Status of the AI model used by LMS_AI (provider, models, availability).
     * Never fails: returns {model_available: false, error: ...} when LMS_AI is down.
     */
    @SuppressWarnings("unchecked")
    public Mono<java.util.Map<String, Object>> getStatus() {
        return webClient.get()
                .uri("/api/health")
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(body -> (java.util.Map<String, Object>) body)
                .timeout(java.time.Duration.ofSeconds(20))
                .onErrorResume(e -> Mono.just(java.util.Map.of(
                        "status", "down",
                        "model_available", false,
                        "error", "LMS_AI injoignable : " + e.getMessage())));
    }

    /**
     * Upload a PDF, TXT or other text document to LMS_AI content routes.
     */
    public Mono<LmsAiContentUploadResponse> uploadTextFile(MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource())
               .filename(file.getOriginalFilename())
               .contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "application/pdf"));

        return webClient.post()
                .uri("/api/content/upload/pdf")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> 
                    clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new LmsAiException(clientResponse.statusCode(), errorBody)))
                )
                .bodyToMono(LmsAiContentUploadResponse.class);
    }

    /**
     * Send a question to the Text Agent and retrieve the response.
     */
    public Mono<LmsAiQuestionResponse> askTextAgent(LmsAiQuestionRequest request) {
        return webClient.post()
                .uri("/api/chat/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> 
                    clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new LmsAiException(clientResponse.statusCode(), errorBody)))
                )
                .bodyToMono(LmsAiQuestionResponse.class);
    }

    /**
     * Upload a local video file to LMS_AI for analysis (Azure transcription + frames).
     */
    public Mono<LmsAiVideoUploadResponse> uploadVideoFile(MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource())
               .filename(file.getOriginalFilename())
               .contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "video/mp4"));

        return webClient.post()
                .uri("/api/content/upload/video")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> 
                    clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new LmsAiException(clientResponse.statusCode(), errorBody)))
                )
                .bodyToMono(LmsAiVideoUploadResponse.class);
    }

    /**
     * Ingest a video or YouTube/Web URL for transcription and indexing.
     */
    public Mono<LmsAiContentUploadResponse> ingestVideoUrl(LmsAiUrlIngestRequest request) {
        return webClient.post()
                .uri("/api/content/ingest/url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> 
                    clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new LmsAiException(clientResponse.statusCode(), errorBody)))
                )
                .bodyToMono(LmsAiContentUploadResponse.class);
    }

    /**
     * Ask a question on an already indexed video transcript.
     */
    public Mono<LmsAiQuestionResponse> askVideoAgent(LmsAiQuestionRequest request) {
        // Under the hood, this calls the same /api/chat/ask which dynamically routes based on content_id type
        return webClient.post()
                .uri("/api/chat/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> 
                    clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new LmsAiException(clientResponse.statusCode(), errorBody)))
                )
                .bodyToMono(LmsAiQuestionResponse.class);
    }
}
