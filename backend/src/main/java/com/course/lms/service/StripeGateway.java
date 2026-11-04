package com.course.lms.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Seul point de contact avec l'API Stripe (MODE TEST).
 * La clé secrète vient de la variable d'environnement STRIPE_SECRET_KEY et ne quitte jamais le backend.
 */
@Service
public class StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

    // Durée de validité d'une session Checkout (minimum imposé par Stripe : 30 minutes)
    private static final long SESSION_LIFETIME_SECONDS = 30 * 60 + 60;

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    void init() {
        if (isConfigured()) {
            if (!secretKey.startsWith("sk_test_") && !secretKey.startsWith("rk_test_")) {
                // Le projet est limité au mode test : une clé live est refusée
                throw new IllegalStateException("STRIPE_SECRET_KEY doit être une clé de TEST (sk_test_...).");
            }
            Stripe.apiKey = secretKey;
            log.info("Stripe configuré en mode test");
        } else {
            log.warn("STRIPE_SECRET_KEY absente : les paiements Stripe sont désactivés");
        }
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public record CreatedSession(String id, String url, Instant expiresAt) {}

    public CreatedSession createCheckoutSession(long paymentId, String courseTitle, long amountInCents, String currency,
                                                String customerEmail, String successUrl, String cancelUrl) throws StripeException {
        ensureConfigured();
        long expiresAt = Instant.now().getEpochSecond() + SESSION_LIFETIME_SECONDS;
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(String.valueOf(paymentId))
                .setCustomerEmail(customerEmail)
                .setExpiresAt(expiresAt)
                .putMetadata("paymentId", String.valueOf(paymentId))
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("paymentId", String.valueOf(paymentId))
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amountInCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(courseTitle)
                                        .build())
                                .build())
                        .build())
                .build();
        // Clé d'idempotence : une même tentative de paiement ne crée jamais deux sessions
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("lms-checkout-" + paymentId).build();
        Session session = Session.create(params, options);
        return new CreatedSession(session.getId(), session.getUrl(), Instant.ofEpochSecond(expiresAt));
    }

    public Session retrieveSession(String sessionId) throws StripeException {
        ensureConfigured();
        return Session.retrieve(sessionId);
    }

    public void expireSession(String sessionId) throws StripeException {
        ensureConfigured();
        Session.retrieve(sessionId).expire();
    }

    public void refund(String paymentIntentId) throws StripeException {
        ensureConfigured();
        Refund.create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build());
    }

    // Vérifie la signature HMAC envoyée par Stripe (en-tête Stripe-Signature)
    public Event verifyWebhook(String payload, String signatureHeader) throws SignatureVerificationException {
        if (!isWebhookConfigured()) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET n'est pas configurée");
        }
        return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Le paiement en ligne n'est pas configuré (STRIPE_SECRET_KEY manquante).");
        }
    }
}
