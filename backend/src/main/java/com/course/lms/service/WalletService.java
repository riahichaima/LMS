package com.course.lms.service;

import com.course.lms.model.Payment;
import com.course.lms.model.User;
import com.course.lms.model.Wallet;
import com.course.lms.model.WalletTransaction;
import com.course.lms.repository.WalletRepository;
import com.course.lms.repository.WalletTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Portefeuilles VIRTUELS (simulation PFE) : chaque vente confirmée est partagée entre l'enseignant (60 %)
 * et la plateforme (40 %). Il n'y a ni retrait ni transfert bancaire.
 * Chaque modification de solde se fait sous verrou d'écriture et laisse une trace dans wallet_transactions.
 */
@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Value("${payments.teacher-share:0.60}")
    private BigDecimal teacherShare;

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
        record(teacherWallet, WalletTransaction.SALE_EARNING, teacherAmount, payment,
                "Vente du cours « " + payment.getCourse().getTitle() + " » (60 %)");

        Wallet platformWallet = lock(getOrCreatePlatformWallet());
        platformWallet.setAvailableBalance(platformWallet.getAvailableBalance().add(platformCommission));
        platformWallet.setTotalEarned(platformWallet.getTotalEarned().add(platformCommission));
        record(platformWallet, WalletTransaction.PLATFORM_COMMISSION, platformCommission, payment,
                "Commission sur « " + payment.getCourse().getTitle() + " » (40 %)");
    }

    // Verrou d'écriture sur la ligne, puis relecture pour travailler sur les soldes à jour
    private Wallet lock(Wallet wallet) {
        Wallet locked = walletRepository.findByIdForUpdate(wallet.getId()).orElseThrow();
        entityManager.refresh(locked);
        return locked;
    }

    private void record(Wallet wallet, String type, BigDecimal amount, Payment payment, String description) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setAvailableAfter(wallet.getAvailableBalance());
        tx.setPayment(payment);
        tx.setDescription(description);
        transactionRepository.save(tx);
    }
}
