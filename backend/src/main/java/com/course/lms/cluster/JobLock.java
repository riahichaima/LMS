package com.course.lms.cluster;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Verrou des tâches planifiées, dans MySQL : avec plusieurs backends, une seule exécution par période
 * (pas de copie envoyée deux fois, pas de rappel de réunion en double). Avec un seul backend, le verrou
 * est toujours obtenu : rien ne change.
 * <p>
 * Le verrou est pris pour une durée fixe, plus courte que la période de la tâche, et n'est pas rendu :
 * l'autre backend ne peut pas relancer la même tâche juste après. Les heures viennent de MySQL,
 * donc un décalage d'horloge entre serveurs ne compte pas.
 */
@Component
public class JobLock {

    private final JdbcTemplate jdbc;
    private final String instance;

    public JobLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String host = System.getenv("HOSTNAME");
        this.instance = (host == null || host.isBlank() ? "backend" : host) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PostConstruct
    void createTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS scheduled_job_lock ("
                + "name VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "locked_until DATETIME(3) NOT NULL, "
                + "locked_by VARCHAR(120) NOT NULL)");
    }

    /** Vrai si ce backend obtient la tâche {@code name} pour la durée {@code hold}. */
    public boolean tryLock(String name, Duration hold) {
        jdbc.update("INSERT IGNORE INTO scheduled_job_lock (name, locked_until, locked_by) "
                + "VALUES (?, '2000-01-01 00:00:00.000', '')", name);
        int updated = jdbc.update("UPDATE scheduled_job_lock "
                        + "SET locked_until = DATE_ADD(NOW(3), INTERVAL ? MICROSECOND), locked_by = ? "
                        + "WHERE name = ? AND locked_until <= NOW(3)",
                hold.toMillis() * 1000, instance, name);
        return updated == 1;
    }

    public String instance() {
        return instance;
    }
}
