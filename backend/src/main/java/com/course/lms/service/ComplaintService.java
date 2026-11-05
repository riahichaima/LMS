package com.course.lms.service;

import com.course.lms.dto.ComplaintDtos.*;
import com.course.lms.model.Complaint;
import com.course.lms.model.SystemLog;
import com.course.lms.model.User;
import com.course.lms.repository.ComplaintRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Réclamations : un étudiant ou un enseignant décrit un problème ; l'administrateur et le super
 * administrateur sont notifiés, le traitent (en cours, résolue, rejetée) et répondent ; l'auteur est notifié.
 */
@Service
@Transactional
public class ComplaintService {

    public static final Set<String> CATEGORIES = Set.of("TECHNIQUE", "COURS", "PAIEMENT", "COMPTE", "ENSEIGNANT", "AUTRE");
    /** Raison donnée par un étudiant / enseignant qui supprime son compte (jamais choisie dans le formulaire). */
    public static final String ACCOUNT_DELETION = "SUPPRESSION";
    private static final Set<String> ADMIN_STATUSES = Set.of(Complaint.IN_PROGRESS, Complaint.RESOLVED, Complaint.REJECTED);

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SystemLogService systemLogService;

    public ComplaintService(ComplaintRepository complaintRepository, UserRepository userRepository,
                            NotificationService notificationService, SystemLogService systemLogService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.systemLogService = systemLogService;
    }

    public ComplaintView create(String email, ComplaintRequest request) {
        User author = findUser(email);
        if (isStaff(author)) throw new IllegalArgumentException("Les administrateurs traitent les réclamations, ils n'en envoient pas.");
        if (request == null) throw new IllegalArgumentException("Formulaire vide.");
        String category = request.category() == null ? "" : request.category().trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("Choisissez le type de problème.");
        String subject = clean(request.subject());
        String message = clean(request.message());
        if (subject.length() < 3 || subject.length() > 150) throw new IllegalArgumentException("L'objet doit contenir entre 3 et 150 caractères.");
        if (message.length() < 10 || message.length() > 4000) throw new IllegalArgumentException("Décrivez le problème en 10 à 4000 caractères.");

        Complaint c = new Complaint();
        c.setAuthor(author);
        c.setCategory(category);
        c.setSubject(subject);
        c.setMessage(message);
        complaintRepository.save(c);

        notificationService.notifyStaff("📝 Nouvelle réclamation : " + subject,
                author.getName() + " (" + roleLabel(author) + ") : « " + message + " »", "COMPLAINT", c.getId());
        systemLogService.record(SystemLog.INFO, "COMPLAINT", "Nouvelle réclamation",
                author.getName() + " a envoyé une réclamation (" + category + ")", author.getEmail());
        return view(c);
    }

    /**
     * Raison de la suppression d'un compte, transmise à l'administration comme une réclamation.
     * Elle reste visible après la suppression (auteur vidé, nom et email conservés).
     */
    public void recordAccountDeletion(User author, String reasonLabel, String details) {
        String role = roleLabel(author);
        Complaint c = new Complaint();
        c.setAuthor(author);
        c.setCategory(ACCOUNT_DELETION);
        c.setSubject(truncate("Suppression du compte " + role + " : " + reasonLabel, 150));
        c.setMessage("Raison : " + reasonLabel
                + (details == null || details.isBlank() ? "" : "\nPrécisions : " + details)
                + "\nCompte : " + author.getName() + " (" + role + ", " + author.getEmail() + ")");
        complaintRepository.save(c);
        notificationService.notifyStaff("🗑️ Compte supprimé : " + author.getName(),
                author.getName() + " (" + role + ") a supprimé son compte. Raison : « " + reasonLabel + " »"
                        + (details == null || details.isBlank() ? "" : " — " + details),
                "COMPLAINT", c.getId());
    }

    @Transactional(readOnly = true)
    public List<ComplaintView> mine(String email) {
        User user = findUser(email);
        return complaintRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(user.getId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintView> all() {
        return complaintRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintStats stats() {
        return new ComplaintStats(complaintRepository.countByStatus(Complaint.OPEN), complaintRepository.countByStatus(Complaint.IN_PROGRESS),
                complaintRepository.countByStatus(Complaint.RESOLVED), complaintRepository.countByStatus(Complaint.REJECTED));
    }

    /** Traitement par un administrateur ; l'auteur reçoit une notification à chaque changement. */
    public ComplaintView handle(Long id, String adminEmail, ComplaintUpdate update) {
        User admin = findUser(adminEmail);
        Complaint c = complaintRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Réclamation introuvable."));
        String status = update == null || update.status() == null ? "" : update.status().trim().toUpperCase(Locale.ROOT);
        if (!ADMIN_STATUSES.contains(status)) throw new IllegalArgumentException("Statut invalide.");
        String response = update.response() == null ? null : clean(update.response());
        if (response != null && response.length() > 4000) throw new IllegalArgumentException("Réponse trop longue (4000 caractères maximum).");
        // Réponse obligatoire pour clore, sauf si l'auteur a supprimé son compte (personne à qui répondre)
        if (c.getAuthor() != null && (Complaint.RESOLVED.equals(status) || Complaint.REJECTED.equals(status)) && (response == null || response.isBlank())
                && (c.getAdminResponse() == null || c.getAdminResponse().isBlank())) {
            throw new IllegalArgumentException("Ajoutez une réponse pour expliquer la décision à l'utilisateur.");
        }

        c.setStatus(status);
        if (response != null && !response.isBlank()) c.setAdminResponse(response);
        c.setHandledBy(admin);
        c.setUpdatedAt(LocalDateTime.now());

        String label = switch (status) {
            case Complaint.RESOLVED -> "résolue ✅";
            case Complaint.REJECTED -> "rejetée";
            default -> "en cours de traitement";
        };
        // Auteur dont le compte a été supprimé : plus personne à notifier
        if (c.getAuthor() != null) notificationService.sendNotification(c.getAuthor(), "📝 Réclamation " + label + " : " + c.getSubject(),
                c.getAdminResponse() != null ? "Réponse de l'administration : « " + c.getAdminResponse() + " »"
                        : "Un administrateur a pris en charge votre réclamation.", "COMPLAINT_UPDATE", c.getId());
        systemLogService.record(SystemLog.INFO, "COMPLAINT", "Réclamation traitée",
                admin.getName() + " a passé la réclamation #" + c.getId() + " en " + status, admin.getEmail());
        return view(c);
    }

    private ComplaintView view(Complaint c) {
        User a = c.getAuthor();
        // Compte supprimé : identité conservée au moment de la réclamation
        return new ComplaintView(c.getId(), c.getCategory(), c.getSubject(), c.getMessage(), c.getStatus(), c.getAdminResponse(),
                a != null ? a.getId() : null,
                a != null ? a.getName() : c.getAuthorName() + " (compte supprimé)",
                a != null ? a.getEmail() : c.getAuthorEmail(),
                a != null ? (a.getRole() != null ? a.getRole().getName() : null) : c.getAuthorRole(),
                c.getHandledBy() != null ? c.getHandledBy().getName() : null, c.getCreatedAt(), c.getUpdatedAt());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }

    private static boolean isStaff(User u) {
        String role = u.getRole() != null ? u.getRole().getName() : "";
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private static String roleLabel(User u) {
        String role = u.getRole() != null ? u.getRole().getName() : "";
        return "TEACHER".equals(role) ? "enseignant" : "étudiant";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
