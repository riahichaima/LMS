package com.course.lms.service;

import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Limite des essais sur une vraie base MySQL : toutes les écritures d'un même compte donnent la même identité. */
@SpringBootTest
class AuthAccountKeyIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;

    @Test
    void caseAndAccentVariantsResolveToTheStoredEmail() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setName("Compte " + id);
        u.setEmail("etudiant-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName("STUDENT").orElseThrow());
        users.save(u);

        // MySQL compare les e-mails sans tenir compte des majuscules ni des accents
        assertThat(authService.accountKey("ETUDIANT-" + id.toUpperCase() + "@TEST.LMS")).isEqualTo(u.getEmail());
        assertThat(authService.accountKey("étudiant-" + id + "@test.lms")).isEqualTo(u.getEmail());
        assertThat(authService.accountKey(" etudiant-" + id + "@test.lms ")).isEqualTo(u.getEmail());
        // E-mail inconnu : l'adresse saisie sert de clé
        assertThat(authService.accountKey("inconnu-" + id + "@test.lms")).isEqualTo("inconnu-" + id + "@test.lms");
    }
}
