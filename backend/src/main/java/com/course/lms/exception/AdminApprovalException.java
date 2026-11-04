package com.course.lms.exception;

// Connexion refusée : compte administrateur en attente de validation ou refusé par le super administrateur
public class AdminApprovalException extends RuntimeException {
    private final String code;

    public AdminApprovalException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
