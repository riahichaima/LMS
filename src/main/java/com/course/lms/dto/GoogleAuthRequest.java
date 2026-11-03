package com.course.lms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Connexion / inscription avec Google.
 * credential : jeton d'identité (JWT) renvoyé par le bouton Google Identity Services.
 * role : rôle choisi sur la page d'inscription (STUDENT, TEACHER ou ADMIN) ; absent depuis la page de connexion.
 */
public record GoogleAuthRequest(@NotBlank(message = "Jeton Google manquant") String credential, String role) {
}
