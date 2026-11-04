package com.course.lms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class LmsAiGlobalExceptionHandler {

    @ExceptionHandler(LmsAiException.class)
    public ResponseEntity<Map<String, Object>> handleLmsAiException(LmsAiException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatusCode().value());
        body.put("error", "LMS_AI Service Error");
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, ex.getStatusCode());
    }
}
