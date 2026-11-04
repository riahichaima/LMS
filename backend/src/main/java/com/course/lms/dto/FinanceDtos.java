package com.course.lms.dto;

import com.course.lms.model.Payment;
import com.course.lms.model.WalletTransaction;
import com.course.lms.model.WithdrawalRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Vues renvoyées par les API de paiement et de gestion financière (montants en USD)
public final class FinanceDtos {

    private FinanceDtos() {}

    public record CheckoutResponse(String checkoutUrl, String sessionId, Long paymentId) {}

    public record PaymentStatusView(Long paymentId, String status, Long courseId, String courseTitle,
                                    BigDecimal amount, String currency, boolean enrolled, String failureMessage) {}

    public record PaymentView(Long id, String status, Long courseId, String courseTitle,
                              Long studentId, String studentName, String studentEmail,
                              Long teacherId, String teacherName,
                              BigDecimal amount, BigDecimal teacherAmount, BigDecimal platformCommission,
                              String currency, String stripeSessionId, LocalDateTime createdAt, LocalDateTime paidAt) {

        public static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getStatus(), p.getCourse().getId(), p.getCourse().getTitle(),
                    p.getStudent().getId(), p.getStudent().getName(), p.getStudent().getEmail(),
                    p.getTeacher().getId(), p.getTeacher().getName(),
                    p.getAmount(), p.getTeacherAmount(), p.getPlatformCommission(),
                    p.getCurrency(), p.getStripeSessionId(), p.getCreatedAt(), p.getPaidAt());
        }
    }

    public record CourseSales(Long courseId, String courseTitle, long sales, BigDecimal revenue, BigDecimal earnings) {}

    public record TeacherEarningsView(long totalSales, BigDecimal totalRevenue, BigDecimal totalEarnings,
                                      BigDecimal availableBalance, BigDecimal pendingBalance, BigDecimal totalWithdrawn,
                                      BigDecimal minWithdrawal, String currency,
                                      List<CourseSales> salesByCourse, List<PaymentView> recentSales,
                                      List<WithdrawalView> withdrawals) {}

    public record WithdrawalView(Long id, Long teacherId, String teacherName, String teacherEmail,
                                 BigDecimal amount, String status, LocalDateTime requestedAt,
                                 String reviewedBy, LocalDateTime reviewedAt, String adminNote,
                                 String transferredBy, LocalDateTime transferredAt, String transferReference) {

        public static WithdrawalView of(WithdrawalRequest w) {
            return new WithdrawalView(w.getId(), w.getTeacher().getId(), w.getTeacher().getName(), w.getTeacher().getEmail(),
                    w.getAmount(), w.getStatus(), w.getRequestedAt(),
                    w.getReviewedBy() != null ? w.getReviewedBy().getName() : null, w.getReviewedAt(), w.getAdminNote(),
                    w.getTransferredBy() != null ? w.getTransferredBy().getName() : null, w.getTransferredAt(),
                    w.getTransferReference());
        }
    }

    public record WithdrawalRequestBody(BigDecimal amount) {}

    public record ReviewBody(String note) {}

    public record TeacherWalletView(Long teacherId, String teacherName, String teacherEmail, long sales,
                                    BigDecimal totalEarned, BigDecimal availableBalance, BigDecimal pendingBalance,
                                    BigDecimal totalWithdrawn) {}

    public record FinanceSummary(long paidPayments, BigDecimal grossRevenue, BigDecimal platformCommission,
                                 BigDecimal teacherEarnings, BigDecimal pendingWithdrawals, BigDecimal totalWithdrawn,
                                 String currency) {}

    public record TransactionView(Long id, String walletType, String walletOwner, String type, BigDecimal amount,
                                  BigDecimal availableAfter, Long paymentId, Long withdrawalId, String description,
                                  LocalDateTime createdAt) {

        public static TransactionView of(WalletTransaction t) {
            return new TransactionView(t.getId(), t.getWallet().getType(),
                    t.getWallet().getOwner() != null ? t.getWallet().getOwner().getName() : "Plateforme",
                    t.getType(), t.getAmount(), t.getAvailableAfter(),
                    t.getPayment() != null ? t.getPayment().getId() : null,
                    t.getWithdrawal() != null ? t.getWithdrawal().getId() : null,
                    t.getDescription(), t.getCreatedAt());
        }
    }

    public record PlatformWalletView(BigDecimal availableBalance, BigDecimal totalCommission,
                                     BigDecimal teacherFundsHeld, BigDecimal totalTeacherEarnings,
                                     BigDecimal totalPaidOut, BigDecimal grossRevenue, long paidPayments,
                                     long transfersToSimulate, String currency) {}
}
