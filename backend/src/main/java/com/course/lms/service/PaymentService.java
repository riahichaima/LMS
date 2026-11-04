package com.course.lms.service;

import com.course.lms.dto.FinanceDtos.CheckoutResponse;
import com.course.lms.dto.FinanceDtos.PaymentStatusView;
import com.course.lms.dto.FinanceDtos.PaymentView;
import com.course.lms.model.Course;
import com.course.lms.model.Payment;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.PaymentRepository;
import com.course.lms.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Paiement des cours via Stripe Checkout (MODE TEST).
 * L'accès au cours n'est accordé qu'après confirmation côté serveur (webhook signé, ou lecture de la
 * session auprès de l'API Stripe) — jamais sur la seule base de la redirection « Success ».
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private StripeGateway stripeGateway;

    @Value("${stripe.currency:usd}")
    private String currency;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ── Création de la session Checkout ─────────────────────────────────────

    @Transactional
    public CheckoutResponse createCheckout(Long courseId, String studentEmail) {
        User student = findUser(studentEmail);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));

        if (!course.isPaid()) {
            throw new IllegalArgumentException("Ce cours est gratuit : inscrivez-vous directement avec « Enroll Now ».");
        }
        if (course.getPrice() == null || course.getPrice().signum() <= 0) {
            throw new IllegalStateException("Le prix de ce cours n'est pas défini.");
        }
        if (enrollmentRepository.findByStudentIdAndCourseId(student.getId(), courseId).isPresent()
                || paymentRepository.existsByStudentIdAndCourseIdAndStatus(student.getId(), courseId, Payment.PAID)) {
            throw new IllegalStateException("Vous avez déjà accès à ce cours.");
        }
        if (!stripeGateway.isConfigured()) {
            throw new IllegalStateException("Le paiement en ligne n'est pas encore configuré sur la plateforme.");
        }

        // Prix relu en base : le montant envoyé par le navigateur n'est jamais utilisé
        BigDecimal price = course.getPrice();

        // Une session encore ouverte au même prix est réutilisée (pas de double paiement)
        for (Payment pending : paymentRepository.findByStudentIdAndCourseIdAndStatus(student.getId(), courseId, Payment.PENDING)) {
            boolean stillOpen = pending.getCheckoutUrl() != null && pending.getSessionExpiresAt() != null
                    && pending.getSessionExpiresAt().isAfter(LocalDateTime.now().plusMinutes(2));
            if (stillOpen && pending.getAmount().compareTo(price) == 0) {
                return new CheckoutResponse(pending.getCheckoutUrl(), pending.getStripeSessionId(), pending.getId());
            }
            // Ancienne session (expirée ou ancien prix) : on la ferme côté Stripe
            closeSession(pending, Payment.EXPIRED);
        }

        Payment payment = new Payment();
        payment.setStudent(student);
        payment.setCourse(course);
        payment.setTeacher(course.getTeacher());
        payment.setAmount(price);
        payment.setCurrency(currency);
        paymentRepository.save(payment);

        try {
            StripeGateway.CreatedSession session = stripeGateway.createCheckoutSession(
                    payment.getId(), course.getTitle(), toCents(price), currency, student.getEmail(),
                    frontendUrl + "/#/payment/success?session_id={CHECKOUT_SESSION_ID}",
                    frontendUrl + "/#/payment/cancel?session_id={CHECKOUT_SESSION_ID}&course_id=" + courseId);
            payment.setStripeSessionId(session.id());
            payment.setCheckoutUrl(session.url());
            payment.setSessionExpiresAt(LocalDateTime.ofInstant(session.expiresAt(), ZoneId.systemDefault()));
            return new CheckoutResponse(session.url(), session.id(), payment.getId());
        } catch (StripeException e) {
            log.error("Création de la session Stripe impossible : {}", e.getMessage());
            payment.setStatus(Payment.FAILED);
            payment.setFailureMessage("Session Stripe non créée : " + e.getMessage());
            throw new IllegalStateException("Le service de paiement est indisponible. Réessayez dans un instant.");
        }
    }

    // ── Confirmation (webhook ou vérification serveur) ──────────────────────

    /**
     * Confirme un paiement à partir des données d'une session Stripe déjà vérifiée (signature du webhook
     * ou lecture via l'API). Idempotent : un paiement déjà confirmé n'est jamais recrédité.
     */
    @Transactional
    public void confirmCheckoutSession(String sessionId, String paymentStatus, Long amountTotal, String sessionCurrency,
                                       String paymentIntentId) {
        Payment payment = paymentRepository.findByStripeSessionIdForUpdate(sessionId).orElse(null);
        if (payment == null) {
            log.warn("Session Stripe inconnue du LMS : {}", sessionId);
            return;
        }
        if (Payment.PAID.equals(payment.getStatus()) || Payment.REFUNDED.equals(payment.getStatus())) {
            return; // déjà traité
        }
        if (!"paid".equals(paymentStatus)) {
            return; // paiement différé : confirmé plus tard par checkout.session.async_payment_succeeded
        }
        if (amountTotal == null || amountTotal != toCents(payment.getAmount()) || !currency.equalsIgnoreCase(sessionCurrency)) {
            log.error("Montant Stripe incohérent pour le paiement {} : {} {}", payment.getId(), amountTotal, sessionCurrency);
            payment.setStatus(Payment.FAILED);
            payment.setFailureMessage("Montant payé incohérent avec le prix du cours.");
            return;
        }
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setPaidAt(LocalDateTime.now());

        // Déjà inscrit (autre paiement confirmé entre-temps) : on rembourse plutôt que de facturer deux fois
        if (enrollmentRepository.findByStudentIdAndCourseId(payment.getStudent().getId(), payment.getCourse().getId()).isPresent()) {
            payment.setStatus(Payment.REFUNDED);
            payment.setFailureMessage("Doublon : l'étudiant avait déjà accès au cours, paiement remboursé.");
            refundQuietly(paymentIntentId);
            return;
        }

        payment.setStatus(Payment.PAID);
        payment.setFailureMessage(null);
        walletService.creditSale(payment);
        enrollmentService.enrollAfterConfirmedPayment(payment.getStudent(), payment.getCourse());
        log.info("Paiement {} confirmé : {} $ (enseignant {} $, plateforme {} $)", payment.getId(),
                payment.getAmount(), payment.getTeacherAmount(), payment.getPlatformCommission());
    }

    @Transactional
    public void markSessionExpired(String sessionId) {
        paymentRepository.findByStripeSessionIdForUpdate(sessionId).ifPresent(p -> {
            if (Payment.PENDING.equals(p.getStatus())) {
                p.setStatus(Payment.EXPIRED);
            }
        });
    }

    @Transactional
    public void markSessionFailed(String sessionId, String message) {
        paymentRepository.findByStripeSessionIdForUpdate(sessionId).ifPresent(p -> {
            if (Payment.PENDING.equals(p.getStatus())) {
                p.setStatus(Payment.FAILED);
                p.setFailureMessage(message);
            }
        });
    }

    // Carte refusée : la session Checkout reste ouverte pour réessayer, on garde seulement le motif
    @Transactional
    public void recordCardDeclined(Long paymentId, String message) {
        paymentRepository.findByIdForUpdate(paymentId).ifPresent(p -> {
            if (Payment.PENDING.equals(p.getStatus())) {
                p.setFailureMessage(message);
            }
        });
    }

    // ── Côté étudiant ───────────────────────────────────────────────────────

    /**
     * Statut affiché sur la page Payment Success. Si le webhook n'est pas encore arrivé, le serveur lit
     * lui-même la session auprès de Stripe : c'est une vérification serveur, pas une confiance dans l'URL.
     */
    @Transactional
    public PaymentStatusView getStatusForStudent(String sessionId, String studentEmail) {
        Payment payment = ownedPayment(sessionId, studentEmail);
        if (Payment.PENDING.equals(payment.getStatus()) && stripeGateway.isConfigured()) {
            try {
                Session session = stripeGateway.retrieveSession(sessionId);
                if ("paid".equals(session.getPaymentStatus())) {
                    confirmCheckoutSession(session.getId(), session.getPaymentStatus(), session.getAmountTotal(),
                            session.getCurrency(), session.getPaymentIntent());
                } else if ("expired".equals(session.getStatus())) {
                    markSessionExpired(sessionId);
                }
            } catch (StripeException e) {
                log.warn("Lecture de la session {} impossible : {}", sessionId, e.getMessage());
            }
            payment = paymentRepository.findByStripeSessionId(sessionId).orElseThrow();
        }
        return toStatusView(payment);
    }

    // Retour depuis « Annuler » sur Stripe : la session est fermée pour qu'elle ne puisse plus être payée
    @Transactional
    public PaymentStatusView cancelForStudent(String sessionId, String studentEmail) {
        Payment payment = ownedPayment(sessionId, studentEmail);
        Payment locked = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();
        if (Payment.PENDING.equals(locked.getStatus())) {
            closeSession(locked, Payment.CANCELLED);
        }
        return toStatusView(locked);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> getStudentPayments(String studentEmail) {
        User student = findUser(studentEmail);
        return paymentRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(PaymentView::of).toList();
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────

    // Montant Stripe en centimes (USD)
    public static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private void closeSession(Payment payment, String newStatus) {
        if (payment.getStripeSessionId() != null && stripeGateway.isConfigured()) {
            try {
                Session session = stripeGateway.retrieveSession(payment.getStripeSessionId());
                if ("paid".equals(session.getPaymentStatus())) {
                    // Payé entre-temps : on confirme au lieu d'annuler
                    confirmCheckoutSession(session.getId(), session.getPaymentStatus(), session.getAmountTotal(),
                            session.getCurrency(), session.getPaymentIntent());
                    return;
                }
                if ("open".equals(session.getStatus())) {
                    stripeGateway.expireSession(payment.getStripeSessionId());
                }
            } catch (StripeException e) {
                log.warn("Fermeture de la session {} impossible : {}", payment.getStripeSessionId(), e.getMessage());
            }
        }
        payment.setStatus(newStatus);
    }

    private void refundQuietly(String paymentIntentId) {
        if (paymentIntentId == null || !stripeGateway.isConfigured()) {
            return;
        }
        try {
            stripeGateway.refund(paymentIntentId);
        } catch (StripeException e) {
            log.error("Remboursement du doublon {} impossible : {}", paymentIntentId, e.getMessage());
        }
    }

    private Payment ownedPayment(String sessionId, String studentEmail) {
        User student = findUser(studentEmail);
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable."));
        if (!payment.getStudent().getId().equals(student.getId())) {
            // Un étudiant ne peut consulter que ses propres paiements
            throw new SecurityException("Ce paiement ne vous appartient pas.");
        }
        return payment;
    }

    private PaymentStatusView toStatusView(Payment p) {
        boolean enrolled = enrollmentRepository.findByStudentIdAndCourseId(p.getStudent().getId(), p.getCourse().getId()).isPresent();
        return new PaymentStatusView(p.getId(), p.getStatus(), p.getCourse().getId(), p.getCourse().getTitle(),
                p.getAmount(), p.getCurrency(), enrolled, p.getFailureMessage());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }
}
