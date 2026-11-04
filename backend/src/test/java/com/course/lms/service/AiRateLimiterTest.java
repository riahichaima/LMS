package com.course.lms.service;

import com.course.lms.service.AiRateLimiter.AiRateLimitException;
import com.course.lms.service.AiRateLimiter.Usage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Limite d'appels à l'IA : fenêtre glissante d'une heure, par utilisateur et par type d'usage. */
class AiRateLimiterTest {

    /** Horloge réglable pour simuler le passage du temps. */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void refusesBeyondTheHourlyLimitWithAWaitTime() {
        MovableClock clock = new MovableClock();
        AiRateLimiter limiter = new AiRateLimiter(3, 10, 30, clock);
        for (int i = 0; i < 3; i++) limiter.check("etudiant@lms.tn", Usage.CHAT);

        assertThatThrownBy(() -> limiter.check("etudiant@lms.tn", Usage.CHAT))
                .isInstanceOf(AiRateLimitException.class)
                .hasMessageContaining("3 questions")
                .satisfies(e -> assertThat(((AiRateLimitException) e).getRetryAfterSeconds()).isPositive());
    }

    @Test
    void windowSlidesAfterOneHour() {
        MovableClock clock = new MovableClock();
        AiRateLimiter limiter = new AiRateLimiter(2, 10, 30, clock);
        limiter.check("a@lms.tn", Usage.CHAT);
        clock.now = clock.now.plus(Duration.ofMinutes(30));
        limiter.check("a@lms.tn", Usage.CHAT);
        assertThatThrownBy(() -> limiter.check("a@lms.tn", Usage.CHAT)).isInstanceOf(AiRateLimitException.class);

        clock.now = clock.now.plus(Duration.ofMinutes(31)); // le 1er appel sort de la fenêtre
        assertThatCode(() -> limiter.check("a@lms.tn", Usage.CHAT)).doesNotThrowAnyException();
    }

    @Test
    void limitsAreSeparatePerUserAndPerUsage() {
        AiRateLimiter limiter = new AiRateLimiter(1, 1, 1, new MovableClock());
        limiter.check("a@lms.tn", Usage.CHAT);
        assertThatCode(() -> limiter.check("b@lms.tn", Usage.CHAT)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("a@lms.tn", Usage.GENERATION)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("a@lms.tn", Usage.CORRECTION)).doesNotThrowAnyException();
    }

    @Test
    void zeroMeansUnlimited() {
        AiRateLimiter limiter = new AiRateLimiter(0, 0, 0, new MovableClock());
        assertThatCode(() -> { for (int i = 0; i < 500; i++) limiter.check("a@lms.tn", Usage.GENERATION); })
                .doesNotThrowAnyException();
    }
}
