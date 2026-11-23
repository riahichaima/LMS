package com.course.lms.config;

import com.course.lms.model.User;
import com.course.lms.model.Wallet;
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

/** Base créée par l'ancienne version (avec retraits) : la migration enlève tables, colonnes et mouvements de retrait. */
@SpringBootTest
class WithdrawalRemovalMigrationTest {

    @Autowired WithdrawalRemovalMigration migration;
    @Autowired WalletRepository wallets;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void legacySchemaIsCleanedAndBalanceIsTheSumOfEarnings() {
        // Schéma de l'ancienne version
        jdbc.execute("ALTER TABLE wallets ADD COLUMN pending_balance DECIMAL(14,2) NOT NULL DEFAULT 0");
        jdbc.execute("ALTER TABLE wallets ADD COLUMN total_withdrawn DECIMAL(14,2) NOT NULL DEFAULT 0");
        jdbc.execute("CREATE TABLE withdrawal_requests (id BIGINT AUTO_INCREMENT PRIMARY KEY, teacher_id BIGINT NOT NULL, "
                + "amount DECIMAL(14,2) NOT NULL, status VARCHAR(20) NOT NULL, "
                + "CONSTRAINT fk_test_withdrawal_teacher FOREIGN KEY (teacher_id) REFERENCES users(id))");
        jdbc.execute("ALTER TABLE wallet_transactions ADD COLUMN withdrawal_id BIGINT NULL, "
                + "ADD CONSTRAINT fk_test_tx_withdrawal FOREIGN KEY (withdrawal_id) REFERENCES withdrawal_requests(id)");

        // Un enseignant a gagné 30 $ puis demandé un retrait de 20 $ (solde 10, en attente 20)
        User teacher = teacher();
        Wallet wallet = new Wallet();
        wallet.setType(Wallet.TEACHER);
        wallet.setOwner(teacher);
        wallet.setAvailableBalance(new BigDecimal("10.00"));
        wallet.setTotalEarned(new BigDecimal("30.00"));
        wallet = wallets.save(wallet);
        jdbc.update("UPDATE wallets SET pending_balance = 20 WHERE id = ?", wallet.getId());
        jdbc.update("INSERT INTO withdrawal_requests (teacher_id, amount, status) VALUES (?, 20, 'PENDING')", teacher.getId());
        Long requestId = jdbc.queryForObject("SELECT MAX(id) FROM withdrawal_requests", Long.class);
        jdbc.update("INSERT INTO wallet_transactions (wallet_id, type, amount, available_after, description, created_at) "
                + "VALUES (?, 'SALE_EARNING', 30, 30, 'Vente', NOW() - INTERVAL 1 HOUR)", wallet.getId());
        jdbc.update("INSERT INTO wallet_transactions (wallet_id, type, amount, available_after, withdrawal_id, description, created_at) "
                + "VALUES (?, 'WITHDRAWAL_HOLD', 20, 10, ?, 'Demande de retrait', NOW())", wallet.getId(), requestId);
        jdbc.update("INSERT INTO notifications (recipient_id, title, message, type, created_at, is_read) "
                + "VALUES (?, 'Retrait', 'Retrait', 'WITHDRAWAL', NOW(), 0)", teacher.getId());

        migration.migrate();
        migration.migrate(); // une 2e fois : rien à faire

        assertThat(count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'withdrawal_requests'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND "
                + "((TABLE_NAME = 'wallets' AND COLUMN_NAME IN ('pending_balance', 'total_withdrawn')) "
                + "OR (TABLE_NAME = 'wallet_transactions' AND COLUMN_NAME = 'withdrawal_id'))")).isZero();
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getAvailableBalance()).isEqualByComparingTo("30.00");
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_id = ?", wallet.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT available_after FROM wallet_transactions WHERE wallet_id = ?",
                BigDecimal.class, wallet.getId())).isEqualByComparingTo("30.00");
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE type = 'WITHDRAWAL'")).isZero();
    }

    private User teacher() {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Migration " + id);
        u.setEmail("migration-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName("TEACHER").orElseThrow());
        return users.save(u);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
