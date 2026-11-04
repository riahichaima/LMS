package com.course.lms.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Journal système (supervision admin / super admin) : actions sensibles, connexions,
 * accès refusés et erreurs serveur. Jamais de mot de passe, de jeton ni de contenu de message.
 */
@Entity
@Table(name = "system_logs", indexes = {
        @Index(name = "idx_system_logs_created", columnList = "created_at"),
        @Index(name = "idx_system_logs_level", columnList = "level"),
        @Index(name = "idx_system_logs_category", columnList = "category")
})
public class SystemLog {

    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";
    public static final String SECURITY = "SECURITY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, length = 30)
    private String category; // AUTH, USER, COURSE, ENROLLMENT, LEARNING, PAYMENT, FINANCE, MESSAGE, AI, SYSTEM

    @Column(nullable = false, length = 120)
    private String action;

    @Column(length = 1000)
    private String message;

    @Column(name = "actor_email", length = 190)
    private String actorEmail;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(length = 255)
    private String path;

    private Integer status;

    @Column(name = "duration_ms")
    private Long durationMs;

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
