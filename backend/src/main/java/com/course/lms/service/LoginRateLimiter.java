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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;

/**
 * Limite des essais de connexion et des envois d'e-mails (inscription, mot de passe oublié, nouvel
 * e-mail de vérification), sur une fenêtre glissante de 15 minutes.
 * <ul>
 *   <li><b>Par compte et par réseau</b> (5) : un attaquant ne bloque que lui-même, jamais le propriétaire
 *       qui se connecte depuis un autre réseau.</li>
 *   <li><b>Par compte, tous réseaux</b> (10 fois plus) : arrête une attaque répartie sur beaucoup d'IP. Les
 *       réseaux où le propriétaire s'est déjà connecté avec succès (30 jours) n'y sont pas soumis : une
 *       attaque répartie ne peut donc pas l'empêcher de se connecter depuis chez lui.</li>
 *   <li><b>Par réseau</b> (20) : une machine qui essaie beaucoup de comptes ; en IPv6, tout le /64.</li>
 * </ul>
 * Chaque essai est réservé <b>avant</b> la vérification, en une seule opération atomique par compteur :
 * des requêtes envoyées en même temps ne peuvent pas dépasser la limite. Une connexion réussie rend
 * l'essai réservé. Mémoire bornée (Caffeine) ; 0 = pas de limite.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_TRACKED_KEYS = 100_000;
    static final Duration KNOWN_NETWORK_DAYS = Duration.ofDays(30);

    private final int maxFailuresPerEmail;
    private final int maxFailuresPerIp;
    private final int maxMailsPerEmail;
    private final int maxMailsPerIp;
    private final Duration window;
    private final Clock clock;
    private final Cache<String, Deque<Instant>> store;
    private final ConcurrentMap<String, Deque<Instant>> events;
    private final Cache<String, Boolean> knownNetworks;

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
        this.knownNetworks = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterWrite(KNOWN_NETWORK_DAYS)
                .executor(Runnable::run)
                .build();
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

    private int perAccountCeiling(int perAccountAndNetwork) {
        return perAccountAndNetwork <= 0 ? 0 : perAccountAndNetwork * 10;
    }

    private boolean isKnownNetwork(String account, String net) {
        return knownNetworks.getIfPresent(account + "|" + net) != null;
    }

    /**
     * Avant de vérifier le mot de passe : réserve un essai sur chaque compteur, ou refuse (429) si l'un est plein.
     * @return true si cet essai vient de remplir un compteur (pour écrire le blocage une seule fois dans le journal)
     */
    public boolean checkLogin(String email, String ip) {
        Instant now = clock.instant();
        String account = normalize(email);
        String net = network(ip);
        String full = "Trop de tentatives de connexion pour ce compte.";
        List<String> taken = new ArrayList<>();
        boolean filled = false;
        try {
            filled |= acquire("login-account-ip:" + account + "|" + net, maxFailuresPerEmail, now, full, taken);
            if (!isKnownNetwork(account, net)) {
                filled |= acquire("login-account:" + account, perAccountCeiling(maxFailuresPerEmail), now, full, taken);
            }
            filled |= acquire("login-ip:" + net, maxFailuresPerIp, now,
                    "Trop de tentatives de connexion depuis votre réseau.", taken);
        } catch (LoginRateLimitException e) {
            taken.forEach(this::refundOne); // refusé : aucun essai consommé
            throw e;
        }
        return filled;
    }

    /** Connexion réussie : essai rendu, échecs de ce compte sur ce réseau oubliés, réseau reconnu 30 jours. */
    public void loginSucceeded(String email, String ip) {
        String account = normalize(email);
        String net = network(ip);
        events.remove("login-account-ip:" + account + "|" + net);
        refundOne("login-account:" + account);
        refundOne("login-ip:" + net);
        knownNetworks.put(account + "|" + net, Boolean.TRUE);
    }

    /** E-mail envoyé (inscription, mot de passe oublié, vérification) : réserve un envoi, ou refuse (429). */
    public void checkMail(String email, String ip) {
        Instant now = clock.instant();
        String account = normalize(email);
        String net = network(ip);
        String full = "Trop de demandes d'e-mail pour cette adresse.";
        List<String> taken = new ArrayList<>();
        try {
            acquire("mail-email-ip:" + account + "|" + net, maxMailsPerEmail, now, full, taken);
            if (!isKnownNetwork(account, net)) {
                acquire("mail-email:" + account, perAccountCeiling(maxMailsPerEmail), now, full, taken);
            }
            acquire("mail-ip:" + net, maxMailsPerIp, now, "Trop de demandes d'e-mail depuis votre réseau.", taken);
        } catch (LoginRateLimitException e) {
            taken.forEach(this::refundOne);
            throw e;
        }
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

    /**
     * Vérifie et réserve en une seule opération atomique (compute) : deux requêtes simultanées ne peuvent pas
     * passer toutes les deux sur la dernière place. Renvoie true si cette réservation remplit le compteur.
     */
    private boolean acquire(String key, int limit, Instant now, String reason, List<String> taken) {
        if (limit <= 0) return false;
        long[] waitSeconds = {0};
        boolean[] filled = {false};
        events.compute(key, (k, history) -> {
            Deque<Instant> h = history == null ? new ArrayDeque<>() : history;
            dropExpired(h, now);
            if (h.size() >= limit) {
                waitSeconds[0] = Math.max(1, Duration.between(now, h.peekFirst().plus(window)).toSeconds());
                return h;
            }
            h.addLast(now);
            filled[0] = h.size() == limit;
            return h;
        });
        if (waitSeconds[0] > 0) {
            long waitMinutes = (waitSeconds[0] + 59) / 60;
            throw new LoginRateLimitException(reason + " Réessayez dans " + waitMinutes + " min.", waitSeconds[0]);
        }
        taken.add(key);
        return filled[0];
    }

    private void refundOne(String key) {
        events.computeIfPresent(key, (k, history) -> {
            history.pollLast();
            return history.isEmpty() ? null : history;
        });
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
