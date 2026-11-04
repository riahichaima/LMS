package com.course.lms.config;

import com.course.lms.model.User;
import com.course.lms.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Comptes créés avant l'ajout de users.created_at : la date d'inscription est reconstituée à partir
 * de la plus ancienne activité réelle connue (cours créé, inscription, message, paiement, journal).
 * Aucune date n'est inventée : sans activité connue, le champ reste vide.
 */
@Component
public class UserCreatedAtBackfill {

    private static final Logger log = LoggerFactory.getLogger(UserCreatedAtBackfill.class);

    private static final List<String> QUERIES = List.of(
            "select min(created_at) from courses where teacher_id = :id",
            "select min(enrolled_at) from enrollments where student_id = :id",
            "select min(sent_at) from messages where sender_id = :id",
            "select min(created_at) from payments where student_id = :id",
            "select min(created_at) from system_logs where actor_email = :email"
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        int updated = 0;
        for (User user : userRepository.findAll()) {
            if (user.getFirstName() == null) user.splitNameIfNeeded();
            if (user.getCreatedAt() != null) continue;
            LocalDateTime earliest = QUERIES.stream()
                    .map(q -> firstDate(q, user))
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            if (earliest != null) {
                user.setCreatedAt(earliest);
                updated++;
            }
        }
        if (updated > 0) log.info("Date d'inscription reconstituée pour {} compte(s)", updated);
    }

    private LocalDateTime firstDate(String sql, User user) {
        try {
            var query = entityManager.createNativeQuery(sql);
            if (sql.contains(":email")) query.setParameter("email", user.getEmail());
            else query.setParameter("id", user.getId());
            Object value = query.getSingleResult();
            if (value instanceof Timestamp ts) return ts.toLocalDateTime();
            if (value instanceof LocalDateTime ldt) return ldt;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
