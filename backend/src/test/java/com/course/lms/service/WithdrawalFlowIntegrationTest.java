package com.course.lms.service;

import com.course.lms.model.User;
import com.course.lms.model.Wallet;
import com.course.lms.model.WithdrawalRequest;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retrait complet sur une vraie base : demande avec compte de réception → approbation → transfert du super admin. */
@SpringBootTest
class WithdrawalFlowIntegrationTest {

    @Autowired WalletService walletService;
    @Autowired WalletRepository wallets;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void teacherReceivesTheMoneyOnTheChosenAccount() {
        User teacher = user("TEACHER");
        User admin = user("ADMIN");
        User superAdmin = users.findByEmail("superadmin@lms.com").orElseThrow();
        Wallet wallet = walletService.getOrCreateTeacherWallet(teacher);
        wallet.setAvailableBalance(new BigDecimal("54.00"));
        wallets.save(wallet);
        long superAdminNotifications = notifications(superAdmin, "Transfert à effectuer");

        WithdrawalRequest request = walletService.requestWithdrawal(teacher, new BigDecimal("50"), "bank", "08 006 0123456789012 34");
        assertThat(request.getPayoutAccount()).isEqualTo("08006012345678901234");
        assertThat(balances(teacher)).containsExactly(new BigDecimal("4.00"), new BigDecimal("50.00"), new BigDecimal("0.00"));

        walletService.approveWithdrawal(request.getId(), admin, null);
        // Le super administrateur sait qu'un transfert l'attend
        assertThat(notifications(superAdmin, "Transfert à effectuer")).isEqualTo(superAdminNotifications + 1);

        WithdrawalRequest paid = walletService.simulateTransfer(request.getId(), superAdmin);
        assertThat(paid.getStatus()).isEqualTo(WithdrawalRequest.PAID);
        assertThat(paid.getTransferReference()).startsWith("SIM-TRF-");
        // 50 $ passent de « en attente » à « déjà retiré »
        assertThat(balances(teacher)).containsExactly(new BigDecimal("4.00"), new BigDecimal("0.00"), new BigDecimal("50.00"));
        String message = jdbc.queryForObject("SELECT message FROM notifications WHERE recipient_id = ? AND title = 'Transfert effectué' "
                + "ORDER BY id DESC LIMIT 1", String.class, teacher.getId());
        assertThat(message).contains("virement bancaire").contains("•••• 1234").contains(paid.getTransferReference());
    }

    @Test
    void payoutAccountIsChecked() {
        User teacher = user("TEACHER");
        Wallet wallet = walletService.getOrCreateTeacherWallet(teacher);
        wallet.setAvailableBalance(new BigDecimal("100.00"));
        wallets.save(wallet);

        assertThatThrownBy(() -> walletService.requestWithdrawal(teacher, new BigDecimal("20"), null, "x"))
                .hasMessageContaining("Choisissez comment recevoir");
        assertThatThrownBy(() -> walletService.requestWithdrawal(teacher, new BigDecimal("20"), "BANK", "1234"))
                .hasMessageContaining("RIB");
        assertThatThrownBy(() -> walletService.requestWithdrawal(teacher, new BigDecimal("20"), "D17", "123"))
                .hasMessageContaining("D17");
        assertThatThrownBy(() -> walletService.requestWithdrawal(teacher, new BigDecimal("20"), "PAYPAL", "pas-un-email"))
                .hasMessageContaining("PayPal");
        assertThat(walletService.requestWithdrawal(teacher, new BigDecimal("20"), "D17", "+216 22 123 456").getPayoutAccount())
                .isEqualTo("22123456");
        assertThat(walletService.requestWithdrawal(teacher, new BigDecimal("20"), "PAYPAL", "Prof@Mail.com").getPayoutAccount())
                .isEqualTo("prof@mail.com");
        assertThat(walletService.requestWithdrawal(teacher, new BigDecimal("20"), "BANK", "FR76 3000 6000 0112 3456 7890 189").getPayoutAccount())
                .isEqualTo("FR7630006000011234567890189");
    }

    private User user(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Retrait " + role + " " + id);
        u.setEmail("retrait-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private BigDecimal[] balances(User teacher) {
        Wallet w = wallets.findById(walletService.getOrCreateTeacherWallet(teacher).getId()).orElseThrow();
        return new BigDecimal[]{w.getAvailableBalance(), w.getPendingBalance(), w.getTotalWithdrawn()};
    }

    private long notifications(User recipient, String title) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND title = ?", Long.class,
                recipient.getId(), title);
        return n == null ? 0 : n;
    }
}
