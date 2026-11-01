package com.course.lms.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Prénom / nom déduits du nom complet (inscription, modification par l'administrateur). */
class UserNameSplitTest {

    private static User user(String name) {
        User u = new User();
        u.setName(name);
        return u;
    }

    @Test
    void splitsOnFirstSpace() {
        User u = user("Chaima Riahi");
        u.splitNameIfNeeded();
        assertThat(u.getFirstName()).isEqualTo("Chaima");
        assertThat(u.getLastName()).isEqualTo("Riahi");
    }

    @Test
    void keepsCompoundLastName() {
        User u = user("Amira Ben Salah");
        u.splitNameIfNeeded();
        assertThat(u.getFirstName()).isEqualTo("Amira");
        assertThat(u.getLastName()).isEqualTo("Ben Salah");
    }

    @Test
    void singleWordHasNoLastName() {
        User u = user("Admin");
        u.splitNameIfNeeded();
        assertThat(u.getFirstName()).isEqualTo("Admin");
        assertThat(u.getLastName()).isNull();
    }

    @Test
    void keepsNamesEditedInProfile() {
        // Prénom composé saisi dans le profil : cohérent avec le nom complet, donc conservé
        User u = user("Mohamed Ali Trabelsi");
        u.setFirstName("Mohamed Ali");
        u.setLastName("Trabelsi");
        u.splitNameIfNeeded();
        assertThat(u.getFirstName()).isEqualTo("Mohamed Ali");
        assertThat(u.getLastName()).isEqualTo("Trabelsi");
    }

    @Test
    void resplitsWhenFullNameChangedElsewhere() {
        User u = user("Nouveau Nom");
        u.setFirstName("Ancien");
        u.setLastName("Prenom");
        u.splitNameIfNeeded();
        assertThat(u.getFirstName()).isEqualTo("Nouveau");
        assertThat(u.getLastName()).isEqualTo("Nom");
    }
}
