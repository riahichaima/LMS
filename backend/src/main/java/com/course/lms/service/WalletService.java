package com.course.lms.service;

import com.course.lms.model.Payment;
import com.course.lms.model.User;
import com.course.lms.model.Wallet;
import com.course.lms.model.WalletTransaction;
import com.course.lms.model.WithdrawalRequest;
import com.course.lms.repository.WalletRepository;
import com.course.lms.repository.WalletTransactionRepository;
import com.course.lms.repository.WithdrawalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Portefeuilles VIRTUELS (simulation PFE) : aucun mouvement bancaire réel.
 * Chaque modification de solde se fait sous verrou d'écriture et laisse une trace dans wallet_transactions.
 */
@Service
public class WalletService {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WalletRepository walletRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRepository;

    @Value("${payments.teacher-share:0.60}")
    private BigDecimal teacherShare;

    @Value("${payments.min-withdrawal:10.00}")
    private BigDecimal minWithdrawal;

    public BigDecimal getMinWithdrawal() {
        return minWithdrawal.setScale(2, RoundingMode.HALF_UP);
    }

    // Part de l'enseignant (60 %) arrondie au centime ; la plateforme reçoit le reste pour que la somme soit exacte
    public BigDecimal teacherAmountOf(BigDecimal amount) {
        return amount.multiply(teacherShare).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Wallet getOrCreateTeacherWallet(User teacher) {
        return walletRepository.findByOwnerIdAndType(teacher.getId(), Wallet.TEACHER).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setType(Wallet.TEACHER);
            wallet.setOwner(teacher);
            return walletRepository.save(wallet);
        });
    }

    @Transactional
    public synchronized Wallet getOrCreatePlatformWallet() {
        return walletRepository.findFirstByTypeAndOwnerIsNull(Wallet.PLATFORM).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setType(Wallet.PLATFORM);
            return walletRepository.save(wallet);
        });
    }

    /**
     * Répartit un paiement confirmé : 60 % au portefeuille de l'enseignant, 40 % à celui de la plateforme.
     * Appelé une seule fois par paiement (le paiement est verrouillé et passe en PAID dans la même transaction).
     */
    @Transactional
    public void creditSale(Payment payment) {
        BigDecimal teacherAmount = teacherAmountOf(payment.getAmount());
        BigDecimal platformCommission = payment.getAmount().subtract(teacherAmount);
        payment.setTeacherAmount(teacherAmount);
        payment.setPlatformCommission(platformCommission);

        Wallet teacherWallet = lock(getOrCreateTeacherWallet(payment.getTeacher()));
        teacherWallet.setAvailableBalance(teacherWallet.getAvailableBalance().add(teacherAmount));
        teacherWallet.setTotalEarned(teacherWallet.getTotalEarned().add(teacherAmount));
        record(teacherWallet, WalletTransaction.SALE_EARNING, teacherAmount, payment, null,
                "Vente du cours « " + payment.getCourse().getTitle() + " » (60 %)");

        Wallet platformWallet = lock(getOrCreatePlatformWallet());
        platformWallet.setAvailableBalance(platformWallet.getAvailableBalance().add(platformCommission));
        platformWallet.setTotalEarned(platformWallet.getTotalEarned().add(platformCommission));
        record(platformWallet, WalletTransaction.PLATFORM_COMMISSION, platformCommission, payment, null,
                "Commission sur « " + payment.getCourse().getTitle() + " » (40 %)");
    }

    /** Moyens de réception proposés à l'enseignant (transfert simulé en mode test). */
    public static final java.util.Map<String, String> PAYOUT_METHODS = java.util.Map.of(
            "BANK", "virement bancaire", "D17", "D17 (La Poste)", "PAYPAL", "PayPal");

    // Demande de retrait : le montant passe du solde disponible au solde en attente
    @Transactional
    public WithdrawalRequest requestWithdrawal(User teacher, BigDecimal amount, String payoutMethod, String payoutAccount) {
        String method = payoutMethod == null ? "" : payoutMethod.trim().toUpperCase(java.util.Locale.ROOT);
        String account = normalizePayoutAccount(method, payoutAccount);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Montant de retrait invalide.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Le montant doit avoir au plus deux décimales.");
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(getMinWithdrawal()) < 0) {
            throw new IllegalArgumentException("Le retrait minimum est de " + getMinWithdrawal() + " $.");
        }
        Wallet wallet = lock(getOrCreateTeacherWallet(teacher));
        if (value.compareTo(wallet.getAvailableBalance()) > 0) {
            throw new IllegalArgumentException("Solde disponible insuffisant (" + wallet.getAvailableBalance() + " $).");
        }
        WithdrawalRequest request = new WithdrawalRequest();
        request.setTeacher(teacher);
        request.setAmount(value);
        request.setPayoutMethod(method);
        request.setPayoutAccount(account);
        withdrawalRepository.save(request);

        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(value));
        wallet.setPendingBalance(wallet.getPendingBalance().add(value));
        record(wallet, WalletTransaction.WITHDRAWAL_HOLD, value, null, request, "Demande de retrait #" + request.getId());
        // Administrateurs et super administrateur (la plateforme peut fonctionner sans administrateur)
        notificationService.notifyStaff("Nouvelle demande de retrait",
                teacher.getName() + " demande un retrait de " + value + " $ par " + PAYOUT_METHODS.get(method) + ".",
                "WITHDRAWAL", request.getId());
        return request;
    }

    // Administrateur : approbation (les fonds restent réservés jusqu'au transfert simulé)
    @Transactional
    public WithdrawalRequest approveWithdrawal(Long id, User admin, String note) {
        WithdrawalRequest request = lockWithdrawal(id, WithdrawalRequest.PENDING);
        request.setStatus(WithdrawalRequest.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminNote(note);
        notificationService.sendNotification(request.getTeacher(), "Retrait approuvé",
                "Votre demande de retrait de " + request.getAmount() + " $ a été approuvée. Le super administrateur va effectuer le transfert"
                        + payoutDestination(request) + " : vous serez prévenu dès qu'il sera fait.", "WITHDRAWAL", request.getId());
        notificationService.notifyRole("SUPER_ADMIN", "Transfert à effectuer",
                request.getTeacher().getName() + " : retrait de " + request.getAmount() + " $ approuvé par " + admin.getName()
                        + ". Effectuez le transfert dans Contrôle financier.", "WITHDRAWAL", request.getId());
        return request;
    }

    // Administrateur : rejet, les fonds redeviennent disponibles pour l'enseignant
    @Transactional
    public WithdrawalRequest rejectWithdrawal(Long id, User admin, String note) {
        WithdrawalRequest request = lockWithdrawal(id, WithdrawalRequest.PENDING);
        request.setStatus(WithdrawalRequest.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminNote(note);

        Wallet wallet = lock(getOrCreateTeacherWallet(request.getTeacher()));
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(request.getAmount()));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(request.getAmount()));
        record(wallet, WalletTransaction.WITHDRAWAL_RELEASE, request.getAmount(), null, request,
                "Retrait #" + request.getId() + " refusé : montant de nouveau disponible");
        notificationService.sendNotification(request.getTeacher(), "Retrait refusé",
                "Votre demande de retrait de " + request.getAmount() + " $ a été refusée"
                        + (note != null && !note.isBlank() ? " : " + note : ".") + " Le montant est de nouveau disponible.",
                "WITHDRAWAL", request.getId());
        return request;
    }

    /**
     * Super administrateur : transfert SIMULÉ vers l'enseignant d'un retrait approuvé.
     * Aucun virement réel : une référence fictive est générée.
     */
    @Transactional
    public WithdrawalRequest simulateTransfer(Long id, User superAdmin) {
        WithdrawalRequest request = lockWithdrawal(id, WithdrawalRequest.APPROVED);
        request.setStatus(WithdrawalRequest.PAID);
        request.setTransferredBy(superAdmin);
        request.setTransferredAt(LocalDateTime.now());
        request.setTransferReference("SIM-TRF-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + request.getId());

        Wallet wallet = lock(getOrCreateTeacherWallet(request.getTeacher()));
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(request.getAmount()));
        wallet.setTotalWithdrawn(wallet.getTotalWithdrawn().add(request.getAmount()));
        record(wallet, WalletTransaction.WITHDRAWAL_PAYOUT, request.getAmount(), null, request,
                "Transfert simulé " + request.getTransferReference());
        notificationService.sendNotification(request.getTeacher(), "Transfert effectué",
                "Transfert de " + request.getAmount() + " $ effectué" + payoutDestination(request)
                        + " (référence " + request.getTransferReference() + ", simulé en mode test).",
                "WITHDRAWAL", request.getId());
        return request;
    }

    // Contrôle du compte de réception selon le moyen choisi
    static String normalizePayoutAccount(String method, String raw) {
        if (!PAYOUT_METHODS.containsKey(method)) {
            throw new IllegalArgumentException("Choisissez comment recevoir l'argent : virement bancaire, D17 ou PayPal.");
        }
        String value = raw == null ? "" : raw.trim();
        switch (method) {
            case "BANK" -> {
                String compact = value.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
                // RIB tunisien (20 chiffres) ou IBAN (2 lettres, 2 chiffres, puis 11 à 30 caractères)
                if (!compact.matches("\\d{20}") && !compact.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")) {
                    throw new IllegalArgumentException("RIB (20 chiffres) ou IBAN invalide.");
                }
                return compact;
            }
            case "D17" -> {
                String digits = value.replaceAll("[\\s.-]", "").replaceFirst("^\\+216", "");
                if (!digits.matches("\\d{8}")) throw new IllegalArgumentException("Numéro D17 invalide (8 chiffres).");
                return digits;
            }
            default -> {
                if (value.length() > 80 || !value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                    throw new IllegalArgumentException("Adresse email PayPal invalide.");
                }
                return value.toLowerCase(java.util.Locale.ROOT);
            }
        }
    }

    // « par virement bancaire sur •••• 1234 » (demandes anciennes sans compte : rien)
    private static String payoutDestination(WithdrawalRequest request) {
        String account = request.getPayoutAccount();
        if (account == null || account.isBlank()) return "";
        String visible = "PAYPAL".equals(request.getPayoutMethod()) ? account
                : "•••• " + account.substring(Math.max(0, account.length() - 4));
        return " par " + PAYOUT_METHODS.getOrDefault(request.getPayoutMethod(), "virement") + " sur " + visible;
    }

    private WithdrawalRequest lockWithdrawal(Long id, String expectedStatus) {
        WithdrawalRequest request = withdrawalRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande de retrait introuvable."));
        if (!expectedStatus.equals(request.getStatus())) {
            throw new IllegalStateException("Cette demande est déjà traitée (statut : " + request.getStatus() + ").");
        }
        return request;
    }

    // Verrou d'écriture sur la ligne, puis relecture pour travailler sur les soldes à jour
    private Wallet lock(Wallet wallet) {
        Wallet locked = walletRepository.findByIdForUpdate(wallet.getId()).orElseThrow();
        entityManager.refresh(locked);
        return locked;
    }

    private void record(Wallet wallet, String type, BigDecimal amount, Payment payment, WithdrawalRequest withdrawal, String description) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setAvailableAfter(wallet.getAvailableBalance());
        tx.setPayment(payment);
        tx.setWithdrawal(withdrawal);
        tx.setDescription(description);
        transactionRepository.save(tx);
    }
}
