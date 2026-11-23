package com.course.lms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Suppression des retraits (simulation PFE) : désormais, une vente est seulement partagée entre l'enseignant (60 %)
 * et la plateforme (40 %). Hibernate (ddl-auto=update) n'efface ni table ni colonne : c'est fait ici, une seule fois,
 * sur une base créée par l'ancienne version. Sur une base neuve, rien n'existe et la migration ne fait rien.
 * <ol>
 *   <li>Les mouvements de retrait simulés sont supprimés ; le solde de chaque portefeuille redevient la somme de ses gains.</li>
 *   <li>La colonne wallet_transactions.withdrawal_id (et sa contrainte), la table withdrawal_requests et les colonnes
 *       pending_balance / total_withdrawn des portefeuilles sont supprimées.</li>
 *   <li>Les anciennes notifications de retrait sont effacées.</li>
 * </ol>
 * Exécutée avant les autres tâches de démarrage, pour qu'aucun portefeuille ne soit créé avec l'ancien schéma.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WithdrawalRemovalMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalRemovalMigration.class);

    private final JdbcTemplate jdbc;

    public WithdrawalRemovalMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            migrate();
        } catch (Exception e) {
            log.warn("Suppression des retraits ignorée : {}", e.getMessage());
        }
    }

    public void migrate() {
        boolean legacy = tableExists("withdrawal_requests") || columnExists("wallet_transactions", "withdrawal_id")
                || columnExists("wallets", "pending_balance") || columnExists("wallets", "total_withdrawn");
        if (!legacy) return;

        int movements = jdbc.update("DELETE FROM wallet_transactions WHERE type LIKE 'WITHDRAWAL%'");
        // Solde après chaque mouvement = cumul des ventes du portefeuille, dans l'ordre
        jdbc.update("UPDATE wallet_transactions t JOIN (SELECT id, SUM(amount) OVER (PARTITION BY wallet_id "
                + "ORDER BY created_at, id) AS running FROM wallet_transactions) r ON r.id = t.id SET t.available_after = r.running");
        int wallets = jdbc.update("UPDATE wallets SET available_balance = total_earned WHERE available_balance <> total_earned");

        if (columnExists("wallet_transactions", "withdrawal_id")) {
            List<String> keys = jdbc.queryForList("SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_transactions' AND COLUMN_NAME = 'withdrawal_id' "
                    + "AND REFERENCED_TABLE_NAME IS NOT NULL", String.class);
            for (String key : keys) {
                jdbc.execute("ALTER TABLE wallet_transactions DROP FOREIGN KEY `" + key.replace("`", "") + "`");
            }
            jdbc.execute("ALTER TABLE wallet_transactions DROP COLUMN withdrawal_id");
        }
        jdbc.execute("DROP TABLE IF EXISTS withdrawal_requests");
        for (String column : List.of("pending_balance", "total_withdrawn")) {
            if (columnExists("wallets", column)) jdbc.execute("ALTER TABLE wallets DROP COLUMN " + column);
        }
        int notifications = jdbc.update("DELETE FROM notifications WHERE type = 'WITHDRAWAL'");

        log.info("Retraits supprimés : {} mouvement(s), {} portefeuille(s) recalculé(s), {} notification(s) ; "
                + "table withdrawal_requests et colonnes de retrait retirées", movements, wallets, notifications);
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ?", Integer.class, table);
        return n != null && n > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? AND COLUMN_NAME = ?", Integer.class, table, column);
        return n != null && n > 0;
    }
}
