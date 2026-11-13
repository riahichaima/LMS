package com.course.lms.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;

/**
 * Limite des essais de connexion et des envois d'e-mails (mot de passe oublié, nouvel e-mail de
 * vérification), sur une fenêtre glissante, pour bloquer les attaques par force brute et
 * l'envoi d'e-mails en masse. Deux compteurs : par adresse e-mail (protège le compte) et par IP
 * (bloque une machine qui essaie beaucoup de comptes ; en IPv6, tout le réseau /64). Réglable par
 * variables d'environnement ; 0 = pas de limite.
 * <p>Mémoire bornée : au plus {@link #MAX_TRACKED_KEYS} compteurs (Caffeine garde en priorité les
 * plus utilisés, donc les comptes réellement attaqués), même si on essaie des millions d'adresses.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_TRACKED_KEYS = 100_000;

    private final int maxFailuresPerEmail;
    private final int maxFailuresPerIp;
    private final int maxMailsPerEmail;
    private final int maxMailsPerIp;
    private final Duration window;
    private final Clock clock;
    private final Cache<String, Deque<Instant>> store;
    private final ConcurrentMap<String, Deque<Instant>> events;

    @Autowired
    public LoginRateLimiter(@Value("${app.auth.rate-limit.failures-per-email:5}") int maxFailuresPerEmail,
                            @Value("${app.auth.rate-limit.failures-per-ip:20}") int maxFailuresPerIp,
                            @Value("${app.auth.rate-limit.mails-per-email:5}") int maxMailsPerEmail,
                            @Value("${app.auth.rate-limit.mails-per-ip:20}") int maxMailsPerIp,
                            @Value("${app.auth.rate-limit.window-minutes:15}") int windowMinutes) {
        this(maxFailuresPerEmail, maxFailuresPerIp, maxMailsPerEmail, maxMailsPerIp,
                Duration.ofMinutes(Math.max(1, windowMinutes)), Clock.systemUTC());
    }

    LoginRateLimiter(int maxFailuresPerEmail, int maxFailuresPerIp, int maxMailsPerEmail, int maxMailsPerIp,
                     Duration window, Clock clock) {
        this(maxFailuresPerEmail, maxFailuresPerIp, maxMailsPerEmail, maxMailsPerIp, window, clock, MAX_TRACKED_KEYS);
    }

    LoginRateLimiter(int maxFailuresPerEmail, int maxFailuresPerIp, int maxMailsPerEmail, int maxMailsPerIp,
                     Duration window, Clock clock, int maxTrackedKeys) {
        this.maxFailuresPerEmail = maxFailuresPerEmail;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.maxMailsPerEmail = maxMailsPerEmail;
        this.maxMailsPerIp = maxMailsPerIp;
        this.window = window;
        this.clock = clock;
        this.store = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterAccess(window.plusMinutes(1))
                .executor(Runnable::run)
                .build();
        this.events = store.asMap();
    }

    /** Clé de l'IP : l'adresse IPv4, ou le réseau /64 en IPv6 (un seul abonné peut en changer à volonté). */
    static String network(String ip) {
        if (ip == null || ip.indexOf(':') < 0) return ip == null ? "" : ip;
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress(); // adresse littérale : aucune requête DNS
            if (bytes.length == 16) return HexFormat.of().formatHex(bytes, 0, 8) + "/64";
            return InetAddress.getByAddress(bytes).getHostAddress(); // IPv4 écrite en IPv6 (::ffff:a.b.c.d)
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    /** Avant de vérifier le mot de passe : refuse (429) si l'e-mail ou l'IP a trop d'échecs récents. */
    public void checkLogin(String email, String ip) {
        Instant now = clock.instant();
        refuseIfFull("login-email:" + normalize(email), maxFailuresPerEmail, now,
                "Trop de tentatives de connexion pour ce compte.");
        refuseIfFull("login-ip:" + network(ip), maxFailuresPerIp, now,
                "Trop de tentatives de connexion depuis votre réseau.");
    }

    /**
     * Mot de passe faux : compte un échec pour l'e-mail et pour l'IP.
     * @return true si cet échec vient de bloquer l'e-mail ou l'IP (pour l'écrire une seule fois dans le journal)
     */
    public boolean loginFailed(String email, String ip) {
        Instant now = clock.instant();
        boolean emailBlocked = add("login-email:" + normalize(email), maxFailuresPerEmail, now);
        boolean ipBlocked = add("login-ip:" + network(ip), maxFailuresPerIp, now);
        return emailBlocked || ipBlocked;
    }

    /** Connexion réussie : les échecs de ce compte sont oubliés (ceux de l'IP restent comptés). */
    public void loginSucceeded(String email) {
        events.remove("login-email:" + normalize(email));
    }

    /** Mot de passe oublié ou nouvel e-mail de vérification : compte l'envoi, ou refuse (429) au-delà de la limite. */
    public void checkMail(String email, String ip) {
        Instant now = clock.instant();
        refuseIfFull("mail-email:" + normalize(email), maxMailsPerEmail, now,
                "Trop de demandes d'e-mail pour cette adresse.");
        refuseIfFull("mail-ip:" + network(ip), maxMailsPerIp, now,
                "Trop de demandes d'e-mail depuis votre réseau.");
        add("mail-email:" + normalize(email), maxMailsPerEmail, now);
        add("mail-ip:" + network(ip), maxMailsPerIp, now);
    }

    /** Retire de la mémoire les compteurs dont tous les événements sont sortis de la fenêtre. */
    @Scheduled(fixedDelayString = "${app.auth.rate-limit.cleanup-ms:600000}")
    public void purgeExpired() {
        Instant now = clock.instant();
        for (String key : events.keySet()) {
            events.computeIfPresent(key, (k, history) -> {
                dropExpired(history, now);
                return history.isEmpty() ? null : history;
            });
        }
    }

    int trackedKeys() {
        store.cleanUp();
        return (int) store.estimatedSize();
    }

    // Toutes les lectures et écritures d'un compteur passent par compute : atomiques pour une même clé.

    private void refuseIfFull(String key, int limit, Instant now, String reason) {
        if (limit <= 0) return;
        long[] waitSeconds = {0};
        events.computeIfPresent(key, (k, history) -> {
            dropExpired(history, now);
            if (history.size() >= limit) {
                waitSeconds[0] = Math.max(1, Duration.between(now, history.peekFirst().plus(window)).toSeconds());
            }
            return history.isEmpty() ? null : history;
        });
        if (waitSeconds[0] > 0) {
            long waitMinutes = (waitSeconds[0] + 59) / 60;
            throw new LoginRateLimitException(reason + " Réessayez dans " + waitMinutes + " min.", waitSeconds[0]);
        }
    }

    /** Ajoute un événement ; renvoie true si la limite vient d'être atteinte. */
    private boolean add(String key, int limit, Instant now) {
        if (limit <= 0) return false;
        boolean[] reached = {false};
        events.compute(key, (k, history) -> {
            Deque<Instant> h = history == null ? new ArrayDeque<>() : history;
            dropExpired(h, now);
            h.addLast(now);
            reached[0] = h.size() == limit;
            return h;
        });
        return reached[0];
    }

    private void dropExpired(Deque<Instant> history, Instant now) {
        Instant start = now.minus(window);
        while (!history.isEmpty() && !history.peekFirst().isAfter(start)) {
            history.pollFirst();
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Trop de tentatives (HTTP 429), avec le délai d'attente conseillé en secondes. */
    public static class LoginRateLimitException extends RuntimeException {
        private final long retryAfterSeconds;

        public LoginRateLimitException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
