package com.course.lms.dto;

import java.time.LocalDateTime;

/** Réclamations : formulaire de l'utilisateur, traitement par l'administration, vue renvoyée au frontend. */
public final class ComplaintDtos {

    private ComplaintDtos() {}

    public record ComplaintRequest(String category, String subject, String message) {}

    /** Traitement par un admin : nouveau statut (IN_PROGRESS, RESOLVED, REJECTED) et réponse facultative. */
    public record ComplaintUpdate(String status, String response) {}

    public record ComplaintView(Long id, String category, String subject, String message, String status,
                                String adminResponse, Long authorId, String authorName, String authorEmail,
                                String authorRole, String handledByName, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ComplaintStats(long open, long inProgress, long resolved, long rejected) {}
}
