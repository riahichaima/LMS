package com.course.lms.service;

import com.course.lms.dto.FinanceDtos.*;
import com.course.lms.model.Payment;
import com.course.lms.model.User;
import com.course.lms.model.Wallet;
import com.course.lms.model.WithdrawalRequest;
import com.course.lms.repository.PaymentRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.repository.WalletRepository;
import com.course.lms.repository.WalletTransactionRepository;
import com.course.lms.repository.WithdrawalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Vues financières en lecture seule (enseignant, administrateur, super administrateur)
@Service
@Transactional(readOnly = true)
public class FinanceService {

    private static final String CURRENCY = "usd";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletService walletService;

    // ── Enseignant : uniquement SES revenus (identifié par son token) ───────

    @Transactional
    public TeacherEarningsView teacherEarnings(String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail).orElseThrow();
        Wallet wallet = walletService.getOrCreateTeacherWallet(teacher);
        List<Payment> sales = paymentRepository.findByTeacherIdAndStatusOrderByPaidAtDesc(teacher.getId(), Payment.PAID);

        Map<Long, List<Payment>> byCourse = sales.stream()
                .collect(Collectors.groupingBy(p -> p.getCourse().getId(), LinkedHashMap::new, Collectors.toList()));
        List<CourseSales> salesByCourse = byCourse.values().stream()
                .map(list -> new CourseSales(list.get(0).getCourse().getId(), list.get(0).getCourse().getTitle(), list.size(),
                        sum(list, Payment::getAmount), sum(list, Payment::getTeacherAmount)))
                .sorted(Comparator.comparing(CourseSales::revenue).reversed())
                .toList();

        return new TeacherEarningsView(sales.size(), sum(sales, Payment::getAmount), wallet.getTotalEarned(),
                wallet.getAvailableBalance(), wallet.getPendingBalance(), wallet.getTotalWithdrawn(),
                walletService.getMinWithdrawal(), CURRENCY, salesByCourse,
                sales.stream().limit(20).map(PaymentView::of).toList(),
                withdrawalRepository.findByTeacherIdOrderByRequestedAtDesc(teacher.getId()).stream()
                        .map(WithdrawalView::of).toList());
    }

    // ── Administrateur ──────────────────────────────────────────────────────

    public FinanceSummary summary() {
        List<Payment> paid = paymentRepository.findByStatusOrderByPaidAtDesc(Payment.PAID);
        List<Wallet> teacherWallets = walletRepository.findByType(Wallet.TEACHER);
        return new FinanceSummary(paid.size(), sum(paid, Payment::getAmount), sum(paid, Payment::getPlatformCommission),
                sum(paid, Payment::getTeacherAmount), sum(teacherWallets, Wallet::getPendingBalance),
                sum(teacherWallets, Wallet::getTotalWithdrawn), CURRENCY);
    }

    public List<PaymentView> payments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream().map(PaymentView::of).toList();
    }

    public List<TeacherWalletView> teacherWallets() {
        Map<Long, Long> salesByTeacher = paymentRepository.findByStatusOrderByPaidAtDesc(Payment.PAID).stream()
                .collect(Collectors.groupingBy(p -> p.getTeacher().getId(), Collectors.counting()));
        return walletRepository.findByType(Wallet.TEACHER).stream()
                .map(w -> new TeacherWalletView(w.getOwner().getId(), w.getOwner().getName(), w.getOwner().getEmail(),
                        salesByTeacher.getOrDefault(w.getOwner().getId(), 0L), w.getTotalEarned(), w.getAvailableBalance(),
                        w.getPendingBalance(), w.getTotalWithdrawn()))
                .sorted(Comparator.comparing(TeacherWalletView::totalEarned).reversed())
                .toList();
    }

    public List<WithdrawalView> withdrawals() {
        return withdrawalRepository.findAllByOrderByRequestedAtDesc().stream().map(WithdrawalView::of).toList();
    }

    // ── Super administrateur ────────────────────────────────────────────────

    @Transactional
    public PlatformWalletView platformWallet() {
        Wallet platform = walletService.getOrCreatePlatformWallet();
        List<Wallet> teacherWallets = walletRepository.findByType(Wallet.TEACHER);
        List<Payment> paid = paymentRepository.findByStatusOrderByPaidAtDesc(Payment.PAID);
        long toTransfer = withdrawalRepository.findByStatusInOrderByRequestedAtDesc(List.of(WithdrawalRequest.APPROVED)).size();
        BigDecimal held = sum(teacherWallets, Wallet::getAvailableBalance).add(sum(teacherWallets, Wallet::getPendingBalance));
        return new PlatformWalletView(platform.getAvailableBalance(), platform.getTotalEarned(), held,
                sum(teacherWallets, Wallet::getTotalEarned), sum(teacherWallets, Wallet::getTotalWithdrawn),
                sum(paid, Payment::getAmount), paid.size(), toTransfer, CURRENCY);
    }

    public List<TransactionView> transactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream().map(TransactionView::of).toList();
    }

    public List<WithdrawalView> transfers() {
        return withdrawalRepository.findByStatusInOrderByRequestedAtDesc(List.of(WithdrawalRequest.APPROVED, WithdrawalRequest.PAID))
                .stream().map(WithdrawalView::of).toList();
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> value) {
        return items.stream().map(value).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
