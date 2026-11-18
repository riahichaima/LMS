package com.course.lms.service;

import com.course.lms.exception.LmsAiException;
import com.course.lms.model.*;
import com.course.lms.repository.CourseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * RAG de l'administration : envoie au service IA les leçons de TOUS les cours (descriptions et ressources)
 * pour construire l'index, puis lui pose les questions de l'administration. Le service IA découpe les contenus,
 * calcule les vecteurs, retrouve les passages les plus proches et répond en citant ses sources.
 */
@Service
public class RagService {

    private final WebClient webClient;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagService(@Qualifier("lmsAiWebClient") WebClient webClient, CourseRepository courseRepository) {
        this.webClient = webClient;
        this.courseRepository = courseRepository;
    }

    /** Leçons de tous les cours, au format attendu par le service IA (les fichiers sont lus côté IA). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> collectLessons() {
        List<Map<String, Object>> lessons = new ArrayList<>();
        for (Course course : courseRepository.findAll()) {
            for (Chapter chapter : course.getChapters()) {
                for (Lesson lesson : chapter.getLessons()) {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("course_id", course.getId());
                    l.put("course_title", Objects.toString(course.getTitle(), ""));
                    l.put("chapter_title", Objects.toString(chapter.getTitle(), ""));
                    l.put("lesson_title", Objects.toString(lesson.getTitle(), ""));
                    l.put("description", lesson.getDescription());
                    List<Map<String, String>> resources = new ArrayList<>();
                    for (LessonResource r : lesson.getResources()) {
                        Map<String, String> d = new HashMap<>();
                        d.put("type", r.getResourceType() != null ? r.getResourceType().toString() : "");
                        if (r.getTitle() != null) d.put("title", r.getTitle());
                        if (r.getFilePath() != null) d.put("file_path", r.getFilePath());
                        if (r.getExternalUrl() != null) d.put("external_url", r.getExternalUrl());
                        if (r.getMimeType() != null) d.put("mime_type", r.getMimeType());
                        resources.add(d);
                    }
                    l.put("resources", resources);
                    lessons.add(l);
                }
            }
        }
        return lessons;
    }

    public Map<String, Object> rebuildIndex() {
        return call(webClient.post().uri("/api/rag/index").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("lessons", collectLessons())));
    }

    public Map<String, Object> status() {
        return call(webClient.get().uri("/api/rag/status"));
    }

    public Map<String, Object> ask(String question) {
        if (question == null || question.trim().length() < 3) {
            throw new IllegalArgumentException("Posez une question d'au moins 3 caractères.");
        }
        return call(webClient.post().uri("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("question", question.trim())));
    }

    /** Appel au service IA ; ses messages d'erreur (« L'index est vide… ») sont renvoyés tels quels. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> call(WebClient.RequestHeadersSpec<?> request) {
        try {
            return request.retrieve()
                    .onStatus(s -> s.isError(), r -> r.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new LmsAiException(r.statusCode(), detail(body)))))
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientRequestException e) {
            throw new LmsAiException(HttpStatus.SERVICE_UNAVAILABLE, "Service IA injoignable : réessayez dans un instant.");
        }
    }

    private String detail(String body) {
        try {
            Object d = objectMapper.readValue(body, Map.class).get("detail");
            if (d instanceof String s && !s.isBlank()) return s;
        } catch (Exception ignored) {
            // corps non JSON : message générique
        }
        return "Le service IA n'a pas pu répondre.";
    }
}
