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

    /** Plusieurs backends : les appels sont comptés dans Redis, la limite vaut pour l'ensemble des backends. */
    private com.course.lms.cluster.ClusterBus bus;

    @Autowired(required = false)
    void setClusterBus(com.course.lms.cluster.ClusterBus bus) {
        this.bus = bus;
    }

    /**
     * Même fenêtre glissante que la version en mémoire, exécutée d'un bloc dans Redis (atomique) :
     * retire les appels de plus d'une heure, refuse si la limite est atteinte (renvoie l'heure du plus ancien),
     * sinon enregistre l'appel. KEYS[1] = clé ; ARGV = maintenant (ms), fenêtre (ms), limite, identifiant unique.
     */
    private static final org.springframework.data.redis.core.script.RedisScript<java.util.List> SLIDING_WINDOW =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[1]) - tonumber(ARGV[2])) "
                    + "if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then "
                    + "  local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES') "
                    + "  return {0, tonumber(oldest[2])} end "
                    + "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
                    + "return {1, 0}", java.util.List.class);

    private void checkShared(String user, Usage usage, int limit, Instant now) {
        java.util.List<?> r = bus.redis().execute(SLIDING_WINDOW, java.util.List.of("lms:ai:" + usage + ":" + user),
                String.valueOf(now.toEpochMilli()), String.valueOf(WINDOW.toMillis()), String.valueOf(limit),
                now.toEpochMilli() + ":" + java.util.UUID.randomUUID());
        if (((Number) r.get(0)).longValue() == 0) {
            Instant oldest = Instant.ofEpochMilli(((Number) r.get(1)).longValue());
            refuse(limit, usage, Duration.between(now, oldest.plus(WINDOW)));
        }
    }

    private static void refuse(int limit, Usage usage, Duration wait) {
        long waitMinutes = wait.toMinutes() + 1;
        throw new AiRateLimitException(
                "Limite atteinte : " + limit + " " + usage.label + " par heure. Réessayez dans " + waitMinutes + " min.",
                waitMinutes * 60);
    }

    /** Enregistre l'appel, ou refuse (429) si l'utilisateur a atteint sa limite sur la dernière heure. */
    public void check(String user, Usage usage) {
        int limit = limits.get(usage);
        if (limit <= 0 || user == null) return;
        Instant now = clock.instant();
        if (bus != null) {
            try {
                checkShared(user, usage, limit, now);
                return;
            } catch (AiRateLimitException e) {
                throw e;
            } catch (RuntimeException e) {
                // Redis indisponible : la limite reste appliquée, par backend, en mémoire
                org.slf4j.LoggerFactory.getLogger(AiRateLimiter.class).warn("Limite IA comptée localement : {}", e.getMessage());
            }
        }
        Deque<Instant> history = calls.computeIfAbsent(usage + ":" + user, k -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && !history.peekFirst().isAfter(now.minus(WINDOW))) {
                history.pollFirst();
            }
            if (history.size() >= limit) {
                refuse(limit, usage, Duration.between(now, history.peekFirst().plus(WINDOW)));
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
