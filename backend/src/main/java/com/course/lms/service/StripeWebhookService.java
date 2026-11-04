package com.course.lms.service;

import com.course.lms.model.ProcessedStripeEvent;
import com.course.lms.repository.ProcessedStripeEventRepository;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applique les événements Stripe déjà authentifiés (signature vérifiée par StripeGateway).
 * Chaque événement n'est traité qu'une fois : son identifiant est enregistré dans la même transaction.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    @Autowired
    private ProcessedStripeEventRepository processedEventRepository;

    @Autowired
    private PaymentService paymentService;

    @Transactional
    public void handle(Event event) {
        if (processedEventRepository.existsById(event.getId())) {
            log.info("Événement Stripe déjà traité, ignoré : {}", event.getId());
            return;
        }

        switch (event.getType()) {
            // Paiement terminé (carte : payment_status = paid immédiatement)
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                Session session = (Session) dataObject(event);
                paymentService.confirmCheckoutSession(session.getId(), session.getPaymentStatus(),
                        session.getAmountTotal(), session.getCurrency(), session.getPaymentIntent());
            }
            case "checkout.session.async_payment_failed" -> {
                Session session = (Session) dataObject(event);
                paymentService.markSessionFailed(session.getId(), "Le paiement différé a échoué.");
            }
            case "checkout.session.expired" -> {
                Session session = (Session) dataObject(event);
                paymentService.markSessionExpired(session.getId());
            }
            // Carte refusée pendant le Checkout : on garde le motif, l'étudiant peut réessayer
            case "payment_intent.payment_failed" -> {
                PaymentIntent intent = (PaymentIntent) dataObject(event);
                String paymentId = intent.getMetadata() != null ? intent.getMetadata().get("paymentId") : null;
                if (paymentId != null) {
                    String message = intent.getLastPaymentError() != null
                            ? intent.getLastPaymentError().getMessage() : "Paiement refusé.";
                    paymentService.recordCardDeclined(Long.valueOf(paymentId), message);
                }
            }
            default -> log.debug("Événement Stripe non utilisé : {}", event.getType());
        }

        processedEventRepository.save(new ProcessedStripeEvent(event.getId(), event.getType()));
    }

    // Objet de l'événement ; si la version d'API du compte diffère de celle du SDK, désérialisation tolérante
    private StripeObject dataObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalStateException("Événement Stripe illisible : " + event.getId(), e);
            }
        });
    }
}
