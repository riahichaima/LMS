package com.course.lms.service;

import com.course.lms.service.LoginRateLimiter.LoginRateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Limite des essais de connexion et des e-mails : 5 par compte, 20 par IP, sur 15 minutes glissantes. */
class LoginRateLimiterTest {

    /** Horloge réglable pour simuler le passage du temps. */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static LoginRateLimiter limiter(MovableClock clock) {
        return new LoginRateLimiter(5, 20, 5, 20, Duration.ofMinutes(15), clock);
    }

    @Test
    void sixthAttemptAfterFiveWrongPasswordsIsRefusedWithAWaitTime() {
        MovableClock clock = new MovableClock();
        LoginRateLimiter limiter = limiter(clock);
        for (int i = 1; i <= 5; i++) {
            limiter.checkLogin("etudiant@lms.tn", "41.226.1.1");
            boolean blocked = limiter.loginFailed("etudiant@lms.tn", "41.226.1.1");
            assertThat(blocked).isEqualTo(i == 5); // bloqué exactement au 5e échec
        }

        assertThatThrownBy(() -> limiter.checkLogin("etudiant@lms.tn", "41.226.1.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("Trop de tentatives de connexion pour ce compte")
                .hasMessageContaining("15 min")
                .satisfies(e -> assertThat(((LoginRateLimitException) e).getRetryAfterSeconds()).isEqualTo(15 * 60));
    }

    @Test
    void anAttackerCannotLockTheOwnerOut() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 5; i++) limiter.loginFailed("cible@lms.tn", "197.0.0.9"); // l'attaquant
        assertThatThrownBy(() -> limiter.checkLogin("cible@lms.tn", "197.0.0.9"))
                .isInstanceOf(LoginRateLimitException.class); // il se bloque lui-même…
        assertThatCode(() -> limiter.checkLogin("cible@lms.tn", "41.226.1.1"))
                .doesNotThrowAnyException(); // … mais le propriétaire, depuis son réseau, peut se connecter
    }

    @Test
    void anAttackSpreadOverManyNetworksIsStoppedPerAccount() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 50; i++) limiter.loginFailed("cible@lms.tn", "10.0." + i + ".1"); // 50 IP différentes
        assertThatThrownBy(() -> limiter.checkLogin("cible@lms.tn", "197.0.0.9"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("pour ce compte");
    }

    @Test
    void emailIsComparedWithoutCaseOrSpaces() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 5; i++) limiter.loginFailed("Etudiant@LMS.tn ", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkLogin("etudiant@lms.tn", "1.1.1.1"))
                .isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void accountIsFreedOnceTheFifteenMinutesHavePassed() {
        MovableClock clock = new MovableClock();
        LoginRateLimiter limiter = limiter(clock);
        for (int i = 0; i < 5; i++) limiter.loginFailed("a@lms.tn", "1.1.1.1");
        clock.now = clock.now.plus(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("1 min");

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginForgetsTheAccountFailures() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 4; i++) limiter.loginFailed("a@lms.tn", "1.1.1.1");
        limiter.loginSucceeded("a@lms.tn", "1.1.1.1");
        for (int i = 0; i < 4; i++) limiter.loginFailed("a@lms.tn", "1.1.1.1");
        assertThatCode(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void oneMachineTryingManyAccountsIsBlockedByIp() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 20; i++) limiter.loginFailed("compte" + i + "@lms.tn", "41.226.9.9");
        assertThatThrownBy(() -> limiter.checkLogin("nouveau@lms.tn", "41.226.9.9"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("depuis votre réseau");
        assertThatCode(() -> limiter.checkLogin("nouveau@lms.tn", "41.226.9.10")).doesNotThrowAnyException();
    }

    @Test
    void forgotPasswordMailsAreLimitedPerAddress() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 5; i++) limiter.checkMail("a@lms.tn", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkMail("a@lms.tn", "1.1.1.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("Trop de demandes d'e-mail");
        assertThatCode(() -> limiter.checkMail("b@lms.tn", "1.1.1.2")).doesNotThrowAnyException();
    }

    @Test
    void zeroMeansNoLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(0, 0, 0, 0, Duration.ofMinutes(15), new MovableClock());
        for (int i = 0; i < 100; i++) {
            limiter.loginFailed("a@lms.tn", "1.1.1.1");
            limiter.checkMail("a@lms.tn", "1.1.1.1");
        }
        assertThatCode(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1")).doesNotThrowAnyException();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void expiredCountersAreRemovedFromMemory() {
        MovableClock clock = new MovableClock();
        LoginRateLimiter limiter = limiter(clock);
        limiter.loginFailed("a@lms.tn", "1.1.1.1");
        assertThat(limiter.trackedKeys()).isEqualTo(3); // compte + réseau, compte, réseau
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        limiter.purgeExpired();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void ipv6AddressesOfTheSameNetworkShareOneCounter() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        // 20 adresses différentes du même réseau /64 : un seul abonné qui change d'adresse
        for (int i = 1; i <= 20; i++) limiter.loginFailed("compte" + i + "@lms.tn", "2001:db8:1:2::" + Integer.toHexString(i));
        assertThatThrownBy(() -> limiter.checkLogin("autre@lms.tn", "2001:db8:1:2:ffff:ffff:ffff:ffff"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("depuis votre réseau");
        assertThatCode(() -> limiter.checkLogin("autre@lms.tn", "2001:db8:1:3::1")).doesNotThrowAnyException();
        assertThat(LoginRateLimiter.network("::ffff:41.226.1.1")).isEqualTo("41.226.1.1");
        assertThat(LoginRateLimiter.network("41.226.1.1")).isEqualTo("41.226.1.1");
    }

    @Test
    void memoryStaysBoundedWhenFloodedWithRandomAddresses() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 20, 5, 20, Duration.ofMinutes(15), new MovableClock(), 1_000);
        for (int i = 0; i < 2; i++) limiter.loginFailed("cible@lms.tn", "41.226.1.1"); // compte visé
        for (int i = 0; i < 20_000; i++) {
            limiter.checkMail("aleatoire" + i + "@x.tn", "10." + (i / 65536) + "." + (i / 256 % 256) + "." + (i % 256));
        }
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(1_000);
    }
}
