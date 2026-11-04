package com.course.lms.security;

import com.course.lms.model.SystemLog;
import com.course.lms.service.SystemLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Journalise dans system_logs les requêtes qui modifient des données (POST/PUT/DELETE),
 * ainsi que les erreurs serveur (5xx) et les accès refusés (403) sur toutes les requêtes.
 * Placé dans la chaîne Spring Security : les refus 401/403 sont donc aussi visibles.
 * Aucun corps de requête n'est enregistré (mots de passe, messages, fichiers).
 */
public class SystemLogFilter extends OncePerRequestFilter {

    private record Rule(String method, Pattern path, String category, String action) {}

    private static Rule r(String method, String regex, String category, String action) {
        return new Rule(method, Pattern.compile("^" + regex + "$"), category, action);
    }

    private static final List<Rule> RULES = List.of(
            r("POST", "/(auth|admin|teacher|student)/register", "AUTH", "Inscription"),
            r("POST", "/auth/resend-verification", "AUTH", "Renvoi de l'email de vérification"),
            r("POST", "/auth/forgot-password", "AUTH", "Demande de réinitialisation du mot de passe"),
            r("POST", "/auth/reset-password", "AUTH", "Réinitialisation du mot de passe"),
            r("POST", "/api/admin/users", "USER", "Création d'un utilisateur"),
            r("PUT", "/api/admin/users/\\d+", "USER", "Modification d'un utilisateur"),
            r("DELETE", "/api/admin/users/\\d+", "USER", "Suppression d'un utilisateur"),
            r("PUT", "/api/admin/users/\\d+/activate", "USER", "Activation d'un compte"),
            r("PUT", "/api/admin/users/\\d+/deactivate", "USER", "Désactivation d'un compte"),
            r("PUT", "/api/admin/users/\\d+/role", "USER", "Changement de rôle"),
            r("PUT", "/api/super-admin/admins/\\d+/approve", "USER", "Approbation d'un compte admin"),
            r("DELETE", "/api/super-admin/admins/\\d+", "USER", "Refus d'un compte admin"),
            r("POST", "/api/(admin/courses|teacher/courses(/full)?)", "COURSE", "Création d'un cours"),
            r("PUT", "/api/(admin/courses|teacher/courses)/\\d+(/full)?", "COURSE", "Modification d'un cours"),
            r("DELETE", "/api/(admin/courses|teacher/courses)/\\d+", "COURSE", "Suppression d'un cours"),
            r("PUT", "/api/admin/(courses|formations)/\\d+/assign-(teacher|trainer)/\\d+", "COURSE", "Affectation d'un formateur"),
            r("POST", "/api/teacher/chapters", "COURSE", "Ajout d'un chapitre"),
            r("PUT", "/api/teacher/chapters/\\d+", "COURSE", "Modification d'un chapitre"),
            r("DELETE", "/api/teacher/chapters/\\d+", "COURSE", "Suppression d'un chapitre"),
            r("POST", "/api/teacher/lessons", "COURSE", "Ajout d'une leçon"),
            r("PUT", "/api/teacher/lessons/\\d+", "COURSE", "Modification d'une leçon"),
            r("DELETE", "/api/teacher/lessons/\\d+", "COURSE", "Suppression d'une leçon"),
            r("POST", "/api/files/upload", "COURSE", "Envoi de fichier"),
            r("POST", "/api/student/enroll/\\d+", "ENROLLMENT", "Inscription à un cours"),
            r("DELETE", "/api/(student|admin)/enrollments/\\d+", "ENROLLMENT", "Désinscription d'un cours"),
            r("PUT", "/api/student/enrollments/\\d+/(progress|complete)", "LEARNING", "Mise à jour de la progression"),
            r("POST", "/api/student/lessons/\\d+/complete", "LEARNING", "Leçon terminée"),
            r("POST", "/api/student/assignments/\\d+/submit", "LEARNING", "TP rendu"),
            r("POST", "/api/assessments/quizzes/\\d+/submit", "LEARNING", "Quiz passé"),
            r("POST", "/api/assessments/(generate|generate-quiz|generate-assignment)", "AI", "Génération IA de quiz / TP"),
            r("POST", "/api/assessments/submissions/correct", "AI", "Correction IA"),
            r("POST", "/api/student/courses/\\d+/chat", "AI", "Tuteur IA du cours"),
            r("POST", "/api/ai/.*", "AI", "Assistant IA"),
            r("POST", "/api/payments/checkout/\\d+", "PAYMENT", "Ouverture d'un paiement Stripe"),
            r("POST", "/api/payments/session/[^/]+/cancel", "PAYMENT", "Annulation d'un paiement"),
            r("POST", "/api/payments/webhook", "PAYMENT", "Webhook Stripe"),
            r("POST", "/api/teacher/withdrawals", "FINANCE", "Demande de retrait"),
            r("PUT", "/api/admin/finance/withdrawals/\\d+/approve", "FINANCE", "Approbation d'un retrait"),
            r("PUT", "/api/admin/finance/withdrawals/\\d+/reject", "FINANCE", "Refus d'un retrait"),
            r("POST", "/api/super-admin/finance/transfers/\\d+/simulate", "FINANCE", "Transfert simulé"),
            r("POST", "/api/messages", "MESSAGE", "Message envoyé"),
            r("PUT", "/api/feedback/courses/\\d+/rating", "FEEDBACK", "Note d'un cours"),
            r("DELETE", "/api/feedback/courses/\\d+/rating", "FEEDBACK", "Note retirée"),
            r("POST", "/api/feedback/resources/\\d+/reaction", "FEEDBACK", "J'aime / Je n'aime pas"),
            r("POST", "/api/feedback/resources/\\d+/comments", "FEEDBACK", "Commentaire ajouté"),
            r("DELETE", "/api/feedback/comments/\\d+", "FEEDBACK", "Commentaire supprimé"),
            r("POST", "/api/feedback/courses/\\d+/reaction", "FEEDBACK", "J'aime / Je n'aime pas un cours"),
            r("POST", "/api/feedback/courses/\\d+/discussion", "FEEDBACK", "Commentaire sur un cours"),
            r("DELETE", "/api/feedback/course-comments/\\d+", "FEEDBACK", "Commentaire supprimé"),
            r("PUT", "/api/feedback/teachers/\\d+/rating", "FEEDBACK", "Évaluation d'un formateur"),
            r("DELETE", "/api/feedback/teachers/\\d+/rating", "FEEDBACK", "Évaluation retirée"),
            r("PUT", "/api/profiles/me", "USER", "Profil modifié"),
            r("POST", "/api/meetings", "MEETING", "Réunion planifiée"),
            r("PUT", "/api/meetings/\\d+", "MEETING", "Réunion modifiée"),
            r("DELETE", "/api/meetings/\\d+", "MEETING", "Réunion annulée"),
            r("POST", "/api/meetings/\\d+/recordings", "MEETING", "Enregistrement de réunion déposé"),
            r("DELETE", "/api/meetings/recordings/\\d+", "MEETING", "Enregistrement supprimé"),
            r("POST", "/api/profiles/me/avatar", "USER", "Photo de profil modifiée"),
            r("DELETE", "/api/profiles/me/avatar", "USER", "Photo de profil supprimée")
    );

    /** Actions trop fréquentes ou sans intérêt pour la supervision. */
    private static final List<Pattern> IGNORED = List.of(
            Pattern.compile("^/api/notifications/.*"),
            Pattern.compile("^/api/messages/\\d+/read$"),
            Pattern.compile("^/auth/login$") // journalisée par AuthService (succès / échec avec l'email)
    );

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SystemLogService logService;

    public SystemLogFilter(SystemLogService logService) {
        this.logService = logService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/") || path.startsWith("/auth/") || path.matches("^/(admin|teacher|student)/register$"))
                || IGNORED.stream().anyMatch(p -> p.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            int status = failure != null && response.getStatus() < 400 ? 500 : response.getStatus();
            boolean mutating = !READ_METHODS.contains(request.getMethod());
            if (mutating || status >= 500 || status == 403) {
                write(request, status, System.currentTimeMillis() - start, failure);
            }
        }
    }

    private void write(HttpServletRequest request, int status, long duration, Throwable failure) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        Rule rule = RULES.stream().filter(x -> x.method().equals(method) && x.path().matcher(path).matches()).findFirst().orElse(null);

        String category = rule != null ? rule.category() : (status >= 500 ? "SYSTEM" : "API");
        String action = rule != null ? rule.action() : (status >= 500 ? "Erreur serveur" : status == 403 ? "Accès refusé" : method + " " + path);
        String level = status >= 500 ? SystemLog.ERROR
                : (status == 401 || status == 403) ? SystemLog.SECURITY
                : status >= 400 ? SystemLog.WARN : SystemLog.INFO;

        String outcome = status >= 500 ? "erreur serveur" : status == 403 ? "accès refusé" : status == 401 ? "non authentifié"
                : status >= 400 ? "refusé (requête invalide)" : "succès";
        String message = method + " " + path + " → " + status + " (" + outcome + ")";
        if (failure != null) message += " — " + failure.getClass().getSimpleName();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logService.recordRequest(level, category, action, message, auth, request, status, duration);
    }
}
