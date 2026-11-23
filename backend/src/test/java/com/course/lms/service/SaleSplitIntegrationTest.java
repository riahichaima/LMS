package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Vente confirmée sur une vraie base : 60 % au portefeuille de l'enseignant, 40 % à celui de la plateforme, sans retrait. */
@SpringBootTest
class SaleSplitIntegrationTest {

    @Autowired WalletService walletService;
    @Autowired WalletRepository wallets;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;

    @Test
    void saleIsSplitSixtyFortyAndRecorded() {
        User teacher = user("TEACHER");
        User student = user("STUDENT");
        Course course = new Course();
        course.setTitle("Cours vendu");
        course.setStatus("PUBLISHED");
        course.setTeacher(teacher);
        courses.save(course);
        BigDecimal platformBefore = walletService.getOrCreatePlatformWallet().getTotalEarned();

        Payment payment = new Payment();
        payment.setStudent(student);
        payment.setTeacher(teacher);
        payment.setCourse(course);
        payment.setAmount(new BigDecimal("19.99"));
        payment.setStatus(Payment.PAID);
        payment.setPaidAt(java.time.LocalDateTime.now());
        payments.save(payment);
        walletService.creditSale(payment);

        // Arrondi au centime pour l'enseignant, le reste à la plateforme : la somme est exacte
        assertThat(payment.getTeacherAmount()).isEqualByComparingTo("11.99");
        assertThat(payment.getPlatformCommission()).isEqualByComparingTo("8.00");

        Wallet teacherWallet = wallets.findByOwnerIdAndType(teacher.getId(), Wallet.TEACHER).orElseThrow();
        assertThat(teacherWallet.getTotalEarned()).isEqualByComparingTo("11.99");
        assertThat(teacherWallet.getAvailableBalance()).isEqualByComparingTo("11.99");
        Wallet platform = walletService.getOrCreatePlatformWallet();
        assertThat(platform.getTotalEarned().subtract(platformBefore)).isEqualByComparingTo("8.00");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id = ? AND type = 'SALE_EARNING'",
                Integer.class, teacherWallet.getId())).isEqualTo(1);
    }

    private User user(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Vente " + role + " " + id);
        u.setEmail("vente-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }
}
