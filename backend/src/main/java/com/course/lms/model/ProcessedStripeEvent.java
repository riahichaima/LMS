package com.course.lms.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Événements Stripe déjà traités : Stripe peut renvoyer un même webhook plusieurs fois,
// l'identifiant unique de l'événement garantit qu'il n'est appliqué qu'une seule fois.
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedStripeEvent() {}

    public ProcessedStripeEvent(String eventId, String type) {
        this.eventId = eventId;
        this.type = type;
        this.processedAt = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public String getType() { return type; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
