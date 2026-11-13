package com.course.lms.service;

import com.course.lms.service.LoginRateLimiter.LoginRateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limite des essais de connexion et des e-mails : 5 par compte et par réseau, 50 par compte (hors réseaux
 * reconnus), 20 par réseau, sur 15 minutes glissantes. Un essai refusé = une réservation non rendue.
 */
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

    /** Un essai de connexion refusé (mot de passe faux) : réservé, jamais rendu. */
    private static boolean fail(LoginRateLimiter limiter, String email, String ip) {
        return limiter.checkLogin(email, ip);
    }

    @Test
    void sixthAttemptAfterFiveWrongPasswordsIsRefusedWithAWaitTime() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 1; i <= 5; i++) {
            assertThat(fail(limiter, "etudiant@lms.tn", "41.226.1.1")).isEqualTo(i == 5); // compteur rempli au 5e essai
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
        for (int i = 0; i < 5; i++) fail(limiter, "cible@lms.tn", "197.0.0.9"); // l'attaquant
        assertThatThrownBy(() -> limiter.checkLogin("cible@lms.tn", "197.0.0.9"))
                .isInstanceOf(LoginRateLimitException.class); // il se bloque lui-même…
        assertThatCode(() -> limiter.checkLogin("cible@lms.tn", "41.226.1.1"))
                .doesNotThrowAnyException(); // … mais le propriétaire, depuis son réseau, peut se connecter
    }

    @Test
    void anAttackSpreadOverManyNetworksIsStoppedPerAccount() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 50; i++) fail(limiter, "cible@lms.tn", "10.0." + i + ".1"); // 50 réseaux différents
        assertThatThrownBy(() -> limiter.checkLogin("cible@lms.tn", "197.0.0.9"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("pour ce compte");
    }

    @Test
    void theOwnerStillLogsInFromARecognisedNetworkDuringASpreadAttack() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        limiter.checkLogin("cible@lms.tn", "41.226.1.1");
        limiter.loginSucceeded("cible@lms.tn", "41.226.1.1"); // le propriétaire s'est déjà connecté de chez lui
        for (int i = 0; i < 50; i++) fail(limiter, "cible@lms.tn", "10.0." + i + ".1"); // attaque répartie
        assertThatThrownBy(() -> limiter.checkLogin("cible@lms.tn", "197.0.0.9"))
                .isInstanceOf(LoginRateLimitException.class); // nouveau réseau : bloqué
        assertThatCode(() -> limiter.checkLogin("cible@lms.tn", "41.226.1.1"))
                .doesNotThrowAnyException(); // réseau reconnu : le propriétaire passe
    }

    @Test
    void parallelRequestsCannotExceedTheLimit() throws Exception {
        LoginRateLimiter limiter = limiter(new MovableClock());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < 64; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    limiter.checkLogin("cible@lms.tn", "41.226.1.1");
                    allowed.incrementAndGet();
                } catch (LoginRateLimitException refused) { /* attendu au-delà de 5 */ }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(allowed).hasValue(5); // jamais plus de 5, même avec 64 requêtes simultanées
    }

    @Test
    void emailIsComparedWithoutCaseOrSpaces() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 5; i++) fail(limiter, "Etudiant@LMS.tn ", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkLogin("etudiant@lms.tn", "1.1.1.1"))
                .isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void accountIsFreedOnceTheFifteenMinutesHavePassed() {
        MovableClock clock = new MovableClock();
        LoginRateLimiter limiter = limiter(clock);
        for (int i = 0; i < 5; i++) fail(limiter, "a@lms.tn", "1.1.1.1");
        clock.now = clock.now.plus(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("1 min");

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginForgetsTheAccountFailuresAndGivesTheAttemptBack() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 4; i++) fail(limiter, "a@lms.tn", "1.1.1.1");
        limiter.checkLogin("a@lms.tn", "1.1.1.1");
        limiter.loginSucceeded("a@lms.tn", "1.1.1.1");
        for (int i = 0; i < 4; i++) fail(limiter, "a@lms.tn", "1.1.1.1");
        assertThatCode(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void manySuccessfulLoginsBehindOneNetworkNeverBlockIt() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 100; i++) { // une école : 100 étudiants derrière la même IP
            limiter.checkLogin("etudiant" + i + "@lms.tn", "41.226.5.5");
            limiter.loginSucceeded("etudiant" + i + "@lms.tn", "41.226.5.5");
        }
        assertThatCode(() -> limiter.checkLogin("etudiant100@lms.tn", "41.226.5.5")).doesNotThrowAnyException();
    }

    @Test
    void oneMachineTryingManyAccountsIsBlockedByIp() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 20; i++) fail(limiter, "compte" + i + "@lms.tn", "41.226.9.9");
        assertThatThrownBy(() -> limiter.checkLogin("nouveau@lms.tn", "41.226.9.9"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("depuis votre réseau");
        assertThatCode(() -> limiter.checkLogin("nouveau@lms.tn", "41.226.9.10")).doesNotThrowAnyException();
    }

    @Test
    void mailsAreLimitedPerAddressAndNetwork() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 5; i++) limiter.checkMail("a@lms.tn", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkMail("a@lms.tn", "1.1.1.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessageContaining("Trop de demandes d'e-mail");
        // Un tiers ne bloque pas les e-mails du propriétaire, qui les demande depuis son propre réseau
        assertThatCode(() -> limiter.checkMail("a@lms.tn", "41.226.1.1")).doesNotThrowAnyException();
    }

    @Test
    void mailsSpreadOverManyNetworksAreStoppedPerAddress() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 50; i++) limiter.checkMail("a@lms.tn", "10.0." + i + ".1");
        assertThatThrownBy(() -> limiter.checkMail("a@lms.tn", "197.0.0.9"))
                .isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void aRefusedRequestConsumesNothing() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        for (int i = 0; i < 20; i++) fail(limiter, "c" + i + "@lms.tn", "41.226.9.9"); // réseau plein
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> limiter.checkLogin("victime@lms.tn", "41.226.9.9"))
                    .isInstanceOf(LoginRateLimitException.class);
        }
        // Les refus n'ont rien réservé sur le compte « victime » : il reste libre depuis un autre réseau
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> limiter.checkLogin("victime@lms.tn", "41.226.1.1")).doesNotThrowAnyException();
        }
    }

    @Test
    void zeroMeansNoLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(0, 0, 0, 0, Duration.ofMinutes(15), new MovableClock());
        for (int i = 0; i < 100; i++) {
            fail(limiter, "a@lms.tn", "1.1.1.1");
            limiter.checkMail("a@lms.tn", "1.1.1.1");
        }
        assertThatCode(() -> limiter.checkLogin("a@lms.tn", "1.1.1.1")).doesNotThrowAnyException();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void expiredCountersAreRemovedFromMemory() {
        MovableClock clock = new MovableClock();
        LoginRateLimiter limiter = limiter(clock);
        fail(limiter, "a@lms.tn", "1.1.1.1");
        assertThat(limiter.trackedKeys()).isEqualTo(3); // compte + réseau, compte, réseau
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        limiter.purgeExpired();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void ipv6AddressesOfTheSameNetworkShareOneCounter() {
        LoginRateLimiter limiter = limiter(new MovableClock());
        // 20 adresses différentes du même réseau /64 : un seul abonné qui change d'adresse
        for (int i = 1; i <= 20; i++) fail(limiter, "compte" + i + "@lms.tn", "2001:db8:1:2::" + Integer.toHexString(i));
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
        for (int i = 0; i < 2; i++) fail(limiter, "cible@lms.tn", "41.226.1.1"); // compte visé
        for (int i = 0; i < 20_000; i++) {
            limiter.checkMail("aleatoire" + i + "@x.tn", "10." + (i / 65536) + "." + (i / 256 % 256) + "." + (i % 256));
        }
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(1_000);
    }
}
