package com.course.lms.exception;

import org.springframework.http.HttpStatus;

// Connexion Google refusée : jeton invalide, compte inexistant, rôle interdit, service non configuré…
public class GoogleAuthException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public GoogleAuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }

    public HttpStatus getStatus() { return status; }
}
