package com.course.lms.service;

import com.course.lms.model.Course;
import com.course.lms.model.Payment;
import com.course.lms.model.SystemLog;
import com.course.lms.model.User;
import com.course.lms.model.WithdrawalRequest;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Suppression définitive d'un compte, par l'administration ou par la personne elle-même (étudiant / enseignant).
 *
 * <ul>
 *   <li>Données personnelles toujours effacées : notifications, messages, liens email, progression, photo.</li>
 *   <li>Compte sans historique financier : supprimé de la base avec ses inscriptions, copies, portefeuille vide
 *       et, pour un enseignant, ses cours (aucun n'a été vendu).</li>
 *   <li>Compte avec des paiements ou des retraits : l'historique comptable doit rester juste (gains des
 *       enseignants, achats des étudiants). Le compte est alors anonymisé : nom et email effacés, connexion
 *       impossible, cours de l'enseignant retirés de la vente (les acheteurs gardent l'accès).</li>
 * </ul>
 * Dans les deux cas, l'adresse email est libérée : elle peut servir à une nouvelle inscription.
 */
@Service
@Transactional
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final Path AVATAR_DIR = Paths.get("uploads", "avatars").toAbsolutePath().normalize();

    public static final Map<String, String> STUDENT_REASONS = reasons(
            "PRIX", "Les cours sont trop chers",
            "PAIEMENT", "Problème ou retard de paiement",
            "CONTENU", "Les cours ne correspondent pas à mes attentes",
            "SITE", "Le site ne me plaît pas ou est difficile à utiliser",
            "AUTRE", "Autre raison");

    public static final Map<String, String> TEACHER_REASONS = reasons(
            "REVENUS", "Mes revenus sont trop faibles",
            "RETRAIT", "Retard dans le paiement de mes gains (retraits)",
            "OUTILS", "Les outils pour créer mes cours ne me conviennent pas",
            "SITE", "Le site ne me plaît pas ou est difficile à utiliser",
            "AUTRE", "Autre raison");

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final ComplaintService complaintService;
    private final SystemLogService systemLogService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    public AccountDeletionService(UserRepository userRepository, CourseRepository courseRepository, CourseService courseService,
                                  ComplaintService complaintService, SystemLogService systemLogService,
                                  PasswordEncoder passwordEncoder, EntityManager em) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.complaintService = complaintService;
        this.systemLogService = systemLogService;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
    }

    /** Raisons proposées selon le rôle (étudiant et enseignant ont leur propre liste). */
    @Transactional(readOnly = true)
    public Map<String, String> reasonsFor(String email) {
        return reasonsForRole(roleOf(findUser(email)));
    }

    /**
     * Suppression demandée par l'étudiant ou l'enseignant : la raison est envoyée à l'administration
     * (réclamation « Suppression de compte »), puis le compte est supprimé.
     */
    public void deleteOwnAccount(String email, String reason, String details) {
        User user = findUser(email);
        Map<String, String> reasons = reasonsForRole(roleOf(user));
        String code = reason == null ? "" : reason.trim().toUpperCase(Locale.ROOT);
        if (!reasons.containsKey(code)) throw new IllegalArgumentException("Choisissez la raison de la suppression.");
        String text = details == null ? "" : details.trim();
        if ("AUTRE".equals(code) && text.length() < 5) {
            throw new IllegalArgumentException("Écrivez la raison de la suppression (5 caractères minimum).");
        }
        if (text.length() > 1000) throw new IllegalArgumentException("Raison trop longue (1000 caractères maximum).");
        if ("TEACHER".equals(roleOf(user))) checkNoMoneyLeft(user);

        // « Autre raison » : le texte écrit devient la raison affichée à l'administration
        String label = "AUTRE".equals(code) ? (text.length() > 90 ? text.substring(0, 89) + "…" : text) : reasons.get(code);
        complaintService.recordAccountDeletion(user, label, "AUTRE".equals(code) && text.length() <= 90 ? "" : text);
        deleteAccount(user, user.getEmail());
    }

    /**
     * Suppression complète d'un compte (administration ou demande de la personne).
     * @param actorEmail auteur de la suppression pour le journal (null : action système)
     */
    public void deleteAccount(User user, String actorEmail) {
        Long id = user.getId();
        String name = user.getName();
        String email = user.getEmail();
        String role = roleOf(user);
        em.flush(); // réclamation « Suppression de compte » enregistrée avant le détachement de l'auteur

        // Paiements jamais aboutis : aucune valeur comptable
        sql("DELETE FROM payments WHERE student_id = ?1 AND status <> ?2", id, Payment.PAID);
        boolean financialHistory = count("SELECT COUNT(*) FROM payments WHERE student_id = ?1 OR teacher_id = ?1", id) > 0
                || count("SELECT COUNT(*) FROM withdrawal_requests WHERE teacher_id = ?1", id) > 0
                || count("SELECT COUNT(*) FROM wallet_transactions t JOIN wallets w ON w.id = t.wallet_id WHERE w.owner_id = ?1", id) > 0;

        // Données personnelles
        sql("DELETE FROM notifications WHERE recipient_id = ?1", id);
        sql("DELETE FROM messages WHERE sender_id = ?1 OR receiver_id = ?1", id);
        sql("DELETE FROM password_reset_token WHERE user_id = ?1", id);
        sql("DELETE FROM verification_tokens WHERE user_id = ?1", id);
        sql("DELETE FROM lesson_progress WHERE student_id = ?1", id);
        // Réclamations : gardées pour l'administration, avec le nom et l'email de l'auteur
        sql("UPDATE complaints SET author_name = COALESCE(author_name, ?2), author_email = COALESCE(author_email, ?3), "
                + "author_role = COALESCE(author_role, ?4), author_id = NULL WHERE author_id = ?1", id, name, email, role);
        // Décisions prises par un administrateur supprimé : la trace reste, sans le lien vers son compte
        sql("UPDATE withdrawal_requests SET reviewed_by = NULL WHERE reviewed_by = ?1", id);
        sql("UPDATE withdrawal_requests SET transferred_by = NULL WHERE transferred_by = ?1", id);
        sql("UPDATE file_storage SET uploaded_by = NULL WHERE uploaded_by = ?1", id);
        String avatar = user.getAvatarPath();

        if (financialHistory) {
            anonymize(user);
        } else {
            if ("TEACHER".equals(role)) {
                for (Course course : courseRepository.findByTeacherId(id)) {
                    sql("DELETE FROM enrollments WHERE course_id = ?1", course.getId());
                    courseService.deleteCourse(course.getId(), null, true);
                }
            }
            sql("DELETE FROM enrollments WHERE student_id = ?1", id);
            sql("DELETE FROM submissions WHERE student_id = ?1", id);
            sql("DELETE FROM wallets WHERE owner_id = ?1", id);
            em.flush();
            // Les autres données (avis, réactions, commentaires, réunions, copies déposées…) suivent en cascade
            sql("DELETE FROM users WHERE id = ?1", id);
            em.clear();
        }
        deleteAvatarFile(avatar);

        systemLogService.record(SystemLog.WARN, "USER", "Compte supprimé",
                name + " (" + role + ", " + email + ") a été supprimé"
                        + (financialHistory ? " (anonymisé : historique financier conservé)" : "")
                        + (email.equalsIgnoreCase(actorEmail) ? " à sa demande" : ""),
                actorEmail);
        log.info("Compte {} supprimé{}", id, financialHistory ? " (anonymisé)" : "");
    }

    // Un enseignant ne perd pas ses gains : retrait d'abord, suppression ensuite
    private void checkNoMoneyLeft(User teacher) {
        Object total = em.createNativeQuery(
                        "SELECT COALESCE(SUM(available_balance + pending_balance), 0) FROM wallets WHERE owner_id = ?1")
                .setParameter(1, teacher.getId()).getSingleResult();
        BigDecimal balance = total instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(total));
        if (balance.signum() > 0) {
            throw new IllegalStateException("Il vous reste " + balance.stripTrailingZeros().toPlainString()
                    + " sur votre portefeuille : demandez un retrait de vos gains avant de supprimer votre compte.");
        }
        if (count("SELECT COUNT(*) FROM withdrawal_requests WHERE teacher_id = ?1 AND status IN ('"
                + WithdrawalRequest.PENDING + "', '" + WithdrawalRequest.APPROVED + "')", teacher.getId()) > 0) {
            throw new IllegalStateException("Une demande de retrait est en cours : attendez son paiement avant de supprimer votre compte.");
        }
    }

    // Historique financier : compte conservé mais vidé de toute donnée personnelle
    private void anonymize(User user) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setEmail("compte-supprime-" + user.getId() + "-" + suffix + "@supprime.invalid");
        user.setName("Compte supprimé");
        user.setFirstName("Compte");
        user.setLastName("supprimé");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID() + suffix));
        user.setEnabled(false);
        user.setAvatarPath(null);
        userRepository.save(user);
        // Cours de l'enseignant retirés de la vente ; les étudiants qui les ont achetés gardent l'accès
        if ("TEACHER".equals(roleOf(user))) {
            for (Course course : courseRepository.findByTeacherId(user.getId())) {
                course.setStatus("DRAFT");
                courseRepository.save(course);
            }
        }
    }

    private void deleteAvatarFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            Path path = AVATAR_DIR.resolve(fileName).normalize();
            if (path.startsWith(AVATAR_DIR)) Files.deleteIfExists(path);
        } catch (Exception e) {
            log.debug("Photo de profil non supprimée : {}", e.getMessage());
        }
    }

    private Map<String, String> reasonsForRole(String role) {
        if ("STUDENT".equals(role)) return STUDENT_REASONS;
        if ("TEACHER".equals(role)) return TEACHER_REASONS;
        throw new IllegalArgumentException("Seuls les étudiants et les enseignants peuvent supprimer leur compte eux-mêmes.");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }

    private static String roleOf(User user) {
        return user.getRole() != null ? user.getRole().getName() : "";
    }

    private void sql(String query, Object... params) {
        var q = em.createNativeQuery(query);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        q.executeUpdate();
    }

    private long count(String query, Object... params) {
        var q = em.createNativeQuery(query);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        return ((Number) q.getSingleResult()).longValue();
    }

    private static Map<String, String> reasons(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return java.util.Collections.unmodifiableMap(map);
    }

    /** Liste ordonnée { code, libellé } pour l'interface. */
    public static List<Map<String, String>> asList(Map<String, String> reasons) {
        return reasons.entrySet().stream().map(e -> Map.of("code", e.getKey(), "label", e.getValue())).toList();
    }
}
