package com.course.lms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite d'appels à l'IA par utilisateur, sur une fenêtre glissante d'une heure, pour protéger
 * le crédit Azure (un compte ne peut pas vider le budget à lui seul). Limites réglables par
 * variables d'environnement ; 0 = illimité. Les envois automatiques du serveur ne sont pas limités.
 */
@Component
public class AiRateLimiter {

    public enum Usage {
        CHAT("questions à l'assistant IA"),
        GENERATION("générations par l'IA"),
        CORRECTION("corrections par l'IA");

        private final String label;

        Usage(String label) {
            this.label = label;
        }
    }

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<Usage, Integer> limits;
    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(@Value("${app.ai.rate-limit.chat-per-hour:30}") int chatPerHour,
                         @Value("${app.ai.rate-limit.generation-per-hour:10}") int generationPerHour,
                         @Value("${app.ai.rate-limit.correction-per-hour:30}") int correctionPerHour) {
        this(chatPerHour, generationPerHour, correctionPerHour, Clock.systemUTC());
    }

    AiRateLimiter(int chatPerHour, int generationPerHour, int correctionPerHour, Clock clock) {
        this.limits = Map.of(Usage.CHAT, chatPerHour, Usage.GENERATION, generationPerHour, Usage.CORRECTION, correctionPerHour);
        this.clock = clock;
    }

    /** Enregistre l'appel, ou refuse (429) si l'utilisateur a atteint sa limite sur la dernière heure. */
    public void check(String user, Usage usage) {
        int limit = limits.get(usage);
        if (limit <= 0 || user == null) return;
        Instant now = clock.instant();
        Deque<Instant> history = calls.computeIfAbsent(usage + ":" + user, k -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && !history.peekFirst().isAfter(now.minus(WINDOW))) {
                history.pollFirst();
            }
            if (history.size() >= limit) {
                long waitMinutes = Duration.between(now, history.peekFirst().plus(WINDOW)).toMinutes() + 1;
                throw new AiRateLimitException(
                        "Limite atteinte : " + limit + " " + usage.label + " par heure. Réessayez dans " + waitMinutes + " min.",
                        waitMinutes * 60);
            }
            history.addLast(now);
        }
    }

    /** Refus d'un appel à l'IA (HTTP 429), avec le délai d'attente conseillé. */
    public static class AiRateLimitException extends RuntimeException {
        private final long retryAfterSeconds;

        public AiRateLimitException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
