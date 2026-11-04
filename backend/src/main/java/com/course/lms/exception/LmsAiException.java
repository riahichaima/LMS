package com.course.lms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class LmsAiException extends RuntimeException {
    private final HttpStatusCode statusCode;

    public LmsAiException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
