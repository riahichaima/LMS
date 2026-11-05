package com.course.lms.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Demande de retrait d'un enseignant (SIMULÉE : aucun virement réel)
// PENDING -> APPROVED (admin) -> PAID (transfert simulé par le super admin)
//         -> REJECTED (admin, les fonds redeviennent disponibles)
@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String PAID = "PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    // Décision de l'administrateur
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    // Transfert simulé par le super administrateur
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferred_by")
    private User transferredBy;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Column(name = "transfer_reference", length = 60)
    private String transferReference;

    /** Moyen de réception choisi par l'enseignant : BANK (RIB / IBAN), D17 (La Poste), PAYPAL */
    @Column(name = "payout_method", length = 10)
    private String payoutMethod;

    /** Compte de réception : RIB / IBAN, numéro D17 ou email PayPal */
    @Column(name = "payout_account", length = 80)
    private String payoutAccount;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public User getTransferredBy() { return transferredBy; }
    public void setTransferredBy(User transferredBy) { this.transferredBy = transferredBy; }

    public LocalDateTime getTransferredAt() { return transferredAt; }
    public void setTransferredAt(LocalDateTime transferredAt) { this.transferredAt = transferredAt; }

    public String getTransferReference() { return transferReference; }
    public void setTransferReference(String transferReference) { this.transferReference = transferReference; }
    public String getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(String payoutMethod) { this.payoutMethod = payoutMethod; }
    public String getPayoutAccount() { return payoutAccount; }
    public void setPayoutAccount(String payoutAccount) { this.payoutAccount = payoutAccount; }
}
