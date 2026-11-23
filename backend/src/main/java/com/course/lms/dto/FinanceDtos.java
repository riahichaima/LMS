package com.course.lms.dto;

import com.course.lms.model.Payment;
import com.course.lms.model.WalletTransaction;

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

    /** Revenus de l'enseignant : 60 % de chaque vente de ses cours. */
    public record TeacherEarningsView(long totalSales, BigDecimal totalRevenue, BigDecimal totalEarnings, String currency,
                                      List<CourseSales> salesByCourse, List<PaymentView> recentSales) {}

    public record TeacherWalletView(Long teacherId, String teacherName, String teacherEmail, long sales,
                                    BigDecimal totalEarned) {}

    public record FinanceSummary(long paidPayments, BigDecimal grossRevenue, BigDecimal platformCommission,
                                 BigDecimal teacherEarnings, String currency) {}

    public record TransactionView(Long id, String walletType, String walletOwner, String type, BigDecimal amount,
                                  BigDecimal availableAfter, Long paymentId, String description, LocalDateTime createdAt) {

        public static TransactionView of(WalletTransaction t) {
            return new TransactionView(t.getId(), t.getWallet().getType(),
                    t.getWallet().getOwner() != null ? t.getWallet().getOwner().getName() : "Plateforme",
                    t.getType(), t.getAmount(), t.getAvailableAfter(),
                    t.getPayment() != null ? t.getPayment().getId() : null,
                    t.getDescription(), t.getCreatedAt());
        }
    }

    /** Portefeuille de la plateforme : 40 % de chaque vente. */
    public record PlatformWalletView(BigDecimal totalCommission, BigDecimal totalTeacherEarnings,
                                     BigDecimal grossRevenue, long paidPayments, String currency) {}
}
