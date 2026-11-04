package com.course.lms.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Historique des mouvements d'un portefeuille virtuel
@Entity
@Table(name = "wallet_transactions", indexes = @Index(name = "idx_wallet_tx_wallet", columnList = "wallet_id"))
public class WalletTransaction {

    // Part enseignant (60 %) d'une vente confirmée
    public static final String SALE_EARNING = "SALE_EARNING";
    // Commission plateforme (40 %) d'une vente confirmée
    public static final String PLATFORM_COMMISSION = "PLATFORM_COMMISSION";
    // Demande de retrait : disponible -> en attente
    public static final String WITHDRAWAL_HOLD = "WITHDRAWAL_HOLD";
    // Retrait refusé : en attente -> disponible
    public static final String WITHDRAWAL_RELEASE = "WITHDRAWAL_RELEASE";
    // Transfert simulé vers l'enseignant : en attente -> retiré
    public static final String WITHDRAWAL_PAYOUT = "WITHDRAWAL_PAYOUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, length = 30)
    private String type;

    // Montant toujours positif ; le sens dépend du type
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    // Solde disponible après l'opération
    @Column(name = "available_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawal_id")
    private WithdrawalRequest withdrawal;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Wallet getWallet() { return wallet; }
    public void setWallet(Wallet wallet) { this.wallet = wallet; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getAvailableAfter() { return availableAfter; }
    public void setAvailableAfter(BigDecimal availableAfter) { this.availableAfter = availableAfter; }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public WithdrawalRequest getWithdrawal() { return withdrawal; }
    public void setWithdrawal(WithdrawalRequest withdrawal) { this.withdrawal = withdrawal; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
