package com.course.lms.controller;

import com.course.lms.service.AiRateLimiter.AiRateLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Limite d'appels à l'IA atteinte : 429 avec un message lisible par le frontend (err.error.message). */
@RestControllerAdvice
public class AiRateLimitHandler {

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<Map<String, String>> tooManyRequests(AiRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("message", ex.getMessage()));
    }
}
