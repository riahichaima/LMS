package com.course.lms.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Lien de streaming : ni modifiable, ni prolongeable, ni réutilisable pour une autre ressource. */
class StreamLinkSignerTest {

    private static final Pattern LINK = Pattern.compile("/api/lessons/resources/(\\d+)/stream\\?exp=(\\d+)&sig=([A-Za-z0-9_-]+)");
    private final Instant now = Instant.parse("2026-11-25T18:00:00Z");
    private final StreamLinkSigner signer = new StreamLinkSigner("secret-de-test-0123456789abcdefghijklmnopqrstuvwxyz", Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void aSignedLinkIsValidForItsResourceOnly() {
        Matcher m = parse(signer.path(42));
        long exp = Long.parseLong(m.group(2));
        String sig = m.group(3);

        assertThat(exp).isEqualTo(now.plus(StreamLinkSigner.VALIDITY).getEpochSecond());
        assertThat(signer.isValid(42, exp, sig)).isTrue();
        assertThat(signer.isValid(43, exp, sig)).isFalse();          // autre ressource
        assertThat(signer.isValid(42, exp + 3600, sig)).isFalse();   // expiration prolongée
        assertThat(signer.isValid(42, exp, sig.substring(1) + "A")).isFalse(); // signature modifiée
        assertThat(signer.isValid(42, exp, null)).isFalse();
    }

    @Test
    void anExpiredLinkIsRefused() {
        Matcher m = parse(signer.path(42));
        long exp = Long.parseLong(m.group(2));
        StreamLinkSigner later = new StreamLinkSigner("secret-de-test-0123456789abcdefghijklmnopqrstuvwxyz",
                Clock.fixed(now.plus(StreamLinkSigner.VALIDITY).plus(Duration.ofSeconds(1)), ZoneOffset.UTC));
        assertThat(later.isValid(42, exp, m.group(3))).isFalse();
    }

    @Test
    void anotherSecretCannotForgeALink() {
        Matcher m = parse(signer.path(42));
        StreamLinkSigner other = new StreamLinkSigner("un-autre-secret-0123456789abcdefghijklmnopqrstuvwxyz", Clock.fixed(now, ZoneOffset.UTC));
        assertThat(other.isValid(42, Long.parseLong(m.group(2)), m.group(3))).isFalse();
    }

    private static Matcher parse(String link) {
        Matcher m = LINK.matcher(link);
        assertThat(m.matches()).as(link).isTrue();
        return m;
    }
}
