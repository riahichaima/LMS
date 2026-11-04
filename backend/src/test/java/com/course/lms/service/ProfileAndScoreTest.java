package com.course.lms.service;

import com.course.lms.model.Submission;
import com.course.lms.model.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Règles sans base de données : type réel des photos de profil, URL des avatars, notes en %. */
class ProfileAndScoreTest {

    @Test
    void detectsRealImageTypes() {
        assertThat(ProfileService.detectImageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isEqualTo("jpg");
        assertThat(ProfileService.detectImageType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo("png");
        assertThat(ProfileService.detectImageType("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEqualTo("gif");
        assertThat(ProfileService.detectImageType("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isEqualTo("webp");
    }

    @Test
    void rejectsFakeImages() {
        // Script renommé en .png, SVG (risque XSS), fichier vide
        assertThat(ProfileService.detectImageType("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ProfileService.detectImageType("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ProfileService.detectImageType(new byte[0])).isNull();
    }

    @Test
    void avatarUrlIsNullWithoutPhoto() {
        User u = new User();
        assertThat(Avatars.url(u)).isNull();
        u.setAvatarPath("0123456789abcdef0123456789abcdef.png");
        assertThat(Avatars.url(u)).isEqualTo("/api/profiles/avatars/0123456789abcdef0123456789abcdef.png");
    }

    @Test
    void scoreIsComputedOnMaxPoints() {
        Submission s = new Submission();
        s.setPoints(15.0);
        s.setMaxPoints(20.0);
        assertThat(TeacherTrackingService.percent(s)).isEqualTo(75.0);

        s.setPoints(2.0);
        s.setMaxPoints(3.0);
        assertThat(TeacherTrackingService.percent(s)).isEqualTo(66.7); // arrondi à 0,1

        s.setMaxPoints(null); // barème absent : sur 100
        s.setPoints(42.0);
        assertThat(TeacherTrackingService.percent(s)).isEqualTo(42.0);

        s.setPoints(null); // pas encore corrigé
        assertThat(TeacherTrackingService.percent(s)).isNull();
    }
}
