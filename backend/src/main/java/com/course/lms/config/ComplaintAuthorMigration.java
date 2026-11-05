package com.course.lms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Réclamations conservées après la suppression du compte de leur auteur (raison d'une suppression de compte…).
 * Hibernate (ddl-auto=update) ajoute les colonnes author_name / author_email / author_role mais ne rend pas
 * author_id facultatif : c'est fait ici une seule fois, puis l'identité des auteurs existants est recopiée.
 */
@Component
public class ComplaintAuthorMigration {

    private static final Logger log = LoggerFactory.getLogger(ComplaintAuthorMigration.class);

    private final JdbcTemplate jdbc;

    public ComplaintAuthorMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            String nullable = jdbc.queryForObject(
                    "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'complaints' AND COLUMN_NAME = 'author_id'", String.class);
            if ("NO".equalsIgnoreCase(nullable)) {
                jdbc.execute("ALTER TABLE complaints MODIFY author_id BIGINT NULL");
                log.info("complaints.author_id rendu facultatif (réclamations conservées après suppression d'un compte)");
            }
            int copied = jdbc.update("UPDATE complaints c JOIN users u ON u.id = c.author_id LEFT JOIN roles r ON r.id = u.role_id "
                    + "SET c.author_name = u.name, c.author_email = u.email, c.author_role = r.name WHERE c.author_name IS NULL");
            if (copied > 0) log.info("Identité de l'auteur recopiée sur {} réclamation(s)", copied);
        } catch (Exception e) {
            log.warn("Migration des réclamations ignorée : {}", e.getMessage());
        }
    }
}
