package com.course.lms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Liens temporaires signés pour lire une vidéo ou un audio en streaming.
 * <p>
 * La balise {@code <video>} du navigateur ne peut pas envoyer le jeton JWT : sans lien signé, le fichier devait
 * être téléchargé en entier avant de commencer. Le lien porte l'identifiant de la ressource et une date
 * d'expiration, signés en HMAC-SHA256 avec une clé dérivée de JWT_SECRET : il ne peut être ni modifié ni
 * prolongé, et il est valable sur tous les backends (même secret derrière le répartiteur de charge).
 * Il n'est remis qu'à un utilisateur qui a déjà le droit de lire la ressource.
 */
@Component
public class StreamLinkSigner {

    /** Assez pour regarder une longue vidéo et revenir en arrière sans recharger la page. */
    public static final Duration VALIDITY = Duration.ofHours(4);

    private final byte[] key;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public StreamLinkSigner(@Value("${jwt.secret}") String jwtSecret) {
        this(jwtSecret, Clock.systemUTC());
    }

    StreamLinkSigner(String jwtSecret, Clock clock) {
        // Clé propre à cet usage : une signature de lien ne vaut jamais comme signature de jeton
        this.key = hmac(("stream-link:" + jwtSecret).getBytes(StandardCharsets.UTF_8),
                "lms-stream-key".getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    /** Chemin signé de lecture : /api/lessons/resources/{id}/stream?exp=…&sig=… */
    public String path(long resourceId) {
        long exp = clock.instant().plus(VALIDITY).getEpochSecond();
        return "/api/lessons/resources/" + resourceId + "/stream?exp=" + exp + "&sig=" + sign(resourceId, exp);
    }

    /** Vrai si la signature correspond à cette ressource et que le lien n'a pas expiré. */
    public boolean isValid(long resourceId, long exp, String sig) {
        if (sig == null || exp < clock.instant().getEpochSecond()) return false;
        return MessageDigest.isEqual(sign(resourceId, exp).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }

    String sign(long resourceId, long exp) {
        byte[] mac = hmac(key, (resourceId + ":" + exp).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponible", e);
        }
    }
}
