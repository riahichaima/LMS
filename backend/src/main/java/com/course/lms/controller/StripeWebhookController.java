package com.course.lms.controller;

import com.course.lms.service.StripeGateway;
import com.course.lms.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook Stripe (public, sans JWT) : seule la signature HMAC Stripe-Signature fait foi.
 * Le corps est lu brut, tel qu'envoyé par Stripe, pour que la vérification soit possible.
 */
@RestController
@RequestMapping("/api/payments")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Autowired
    private StripeGateway stripeGateway;

    @Autowired
    private StripeWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (!stripeGateway.isWebhookConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook non configuré");
        }
        if (signature == null) {
            return ResponseEntity.badRequest().body("Signature manquante");
        }
        Event event;
        try {
            event = stripeGateway.verifyWebhook(payload, signature);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook Stripe rejeté : signature invalide");
            return ResponseEntity.badRequest().body("Signature invalide");
        }
        // Une erreur de traitement renvoie 500 : Stripe renverra l'événement plus tard
        webhookService.handle(event);
        return ResponseEntity.ok("ok");
    }
}
