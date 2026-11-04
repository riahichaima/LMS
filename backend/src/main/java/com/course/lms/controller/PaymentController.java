package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.CheckoutResponse;
import com.course.lms.dto.FinanceDtos.PaymentStatusView;
import com.course.lms.dto.FinanceDtos.PaymentView;
import com.course.lms.service.PaymentService;
import com.course.lms.service.StripeGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Paiement des cours par l'étudiant (Stripe Checkout, mode test)
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StripeGateway stripeGateway;

    // Le frontend sait si le paiement est disponible ; aucune clé n'est renvoyée
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of("enabled", stripeGateway.isConfigured(), "currency", "usd", "testMode", true));
    }

    // Crée la session Checkout : le prix est relu en base, jamais pris dans la requête
    @PostMapping("/checkout/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<CheckoutResponse> checkout(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(paymentService.createCheckout(courseId, auth.getName()));
    }

    // Statut pour la page Payment Success (confirmé côté serveur uniquement)
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PaymentStatusView> status(@PathVariable String sessionId, Authentication auth) {
        return ResponseEntity.ok(paymentService.getStatusForStudent(sessionId, auth.getName()));
    }

    @PostMapping("/session/{sessionId}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PaymentStatusView> cancel(@PathVariable String sessionId, Authentication auth) {
        return ResponseEntity.ok(paymentService.cancelForStudent(sessionId, auth.getName()));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<PaymentView>> myPayments(Authentication auth) {
        return ResponseEntity.ok(paymentService.getStudentPayments(auth.getName()));
    }
}
