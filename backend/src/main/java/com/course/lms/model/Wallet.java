package com.course.lms.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Portefeuille VIRTUEL (simulation PFE) : un par enseignant, un seul pour la plateforme.
// Aucun transfert bancaire réel n'est effectué.
@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(name = "uk_wallet_owner_type", columnNames = {"owner_id", "type"}))
public class Wallet {

    public static final String TEACHER = "TEACHER";
    public static final String PLATFORM = "PLATFORM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type;

    // Enseignant propriétaire (null pour le portefeuille de la plateforme)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Montant retirable
    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    // Montant réservé par des demandes de retrait en cours de traitement
    @Column(name = "pending_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    // Cumul des gains (enseignant) ou des commissions (plateforme)
    @Column(name = "total_earned", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEarned = BigDecimal.ZERO;

    // Cumul des transferts simulés vers l'enseignant
    @Column(name = "total_withdrawn", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalWithdrawn = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "usd";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }

    public BigDecimal getPendingBalance() { return pendingBalance; }
    public void setPendingBalance(BigDecimal pendingBalance) { this.pendingBalance = pendingBalance; }

    public BigDecimal getTotalEarned() { return totalEarned; }
    public void setTotalEarned(BigDecimal totalEarned) { this.totalEarned = totalEarned; }

    public BigDecimal getTotalWithdrawn() { return totalWithdrawn; }
    public void setTotalWithdrawn(BigDecimal totalWithdrawn) { this.totalWithdrawn = totalWithdrawn; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
