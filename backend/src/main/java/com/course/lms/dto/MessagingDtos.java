package com.course.lms.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Vues de la messagerie interne et des notifications (jamais d'entité JPA renvoyée directement). */
public final class MessagingDtos {

    private MessagingDtos() {}

    public record NotificationView(Long id, String title, String message, String type, Long relatedId,
                                   LocalDateTime createdAt, boolean read) {}

    /** Interlocuteur autorisé : formateur d'un cours suivi, ou étudiant inscrit à un de ses cours. */
    public record ContactView(Long userId, String name, String email, String role, String avatarUrl, List<String> courses,
                              String lastMessage, LocalDateTime lastMessageAt, boolean lastFromMe, long unreadCount) {}

    /** Poussé à l'expéditeur quand le destinataire ouvre la conversation. */
    public record ReadReceipt(Long readerId, Long conversationWith) {}
}
