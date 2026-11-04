package com.course.lms.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Paiement d'un cours via Stripe Checkout. Aucune donnée de carte n'est stockée :
// seuls les identifiants Stripe (session, payment intent) sont conservés.
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_student_course", columnList = "student_id, course_id"),
        @Index(name = "idx_payment_status", columnList = "status")
})
public class Payment {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";
    // Payé alors que l'étudiant était déjà inscrit : remboursé automatiquement
    public static final String REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // Teacher du cours au moment de l'achat (destinataire des 60 %)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    // Prix lu en base au moment de la création de la session
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "usd";

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "teacher_amount", precision = 12, scale = 2)
    private BigDecimal teacherAmount;

    @Column(name = "platform_commission", precision = 12, scale = 2)
    private BigDecimal platformCommission;

    @Column(name = "stripe_session_id", unique = true)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    // URL Stripe Checkout, réutilisée tant que la session est ouverte (évite les doubles sessions)
    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "session_expires_at")
    private LocalDateTime sessionExpiresAt;

    // Dernier refus de carte signalé par Stripe (la session reste ouverte pour réessayer)
    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTeacherAmount() { return teacherAmount; }
    public void setTeacherAmount(BigDecimal teacherAmount) { this.teacherAmount = teacherAmount; }

    public BigDecimal getPlatformCommission() { return platformCommission; }
    public void setPlatformCommission(BigDecimal platformCommission) { this.platformCommission = platformCommission; }

    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String stripeSessionId) { this.stripeSessionId = stripeSessionId; }

    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String stripePaymentIntentId) { this.stripePaymentIntentId = stripePaymentIntentId; }

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

    public LocalDateTime getSessionExpiresAt() { return sessionExpiresAt; }
    public void setSessionExpiresAt(LocalDateTime sessionExpiresAt) { this.sessionExpiresAt = sessionExpiresAt; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
