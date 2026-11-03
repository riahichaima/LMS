package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Date limite de rendu et temps de travail des quiz / TP (générés par l'IA ou importés en fichier).
 *
 * <ul>
 *   <li>Après la date limite, plus aucun rendu n'est accepté.</li>
 *   <li>Minuté : le chrono démarre quand l'étudiant clique sur « Commencer » (heure fixée par le serveur)
 *       et se termine au bout de la durée, ou à la date limite si elle arrive avant. Un seul passage.</li>
 *   <li>Les réponses en cours sont enregistrées (brouillon) ; à la fin du temps elles sont envoyées
 *       automatiquement, même si l'étudiant a fermé la page ({@link AssessmentAutoSubmitJob}).</li>
 * </ul>
 */
@Service
@Transactional
public class AssessmentScheduleService {

    /** Marge réseau : une copie envoyée par le navigateur juste à la fin du temps est encore acceptée. */
    static final long GRACE_SECONDS = 60;
    private static final int MAX_DRAFT = 200_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZoneId.of("Africa/Tunis"));

    /** Règles d'un quiz / TP et cours auquel il appartient. */
    public record Rules(String kind, Long id, String title, Course course, Instant dueAt, Integer durationMinutes) {
        boolean timed() { return durationMinutes != null && durationMinutes > 0; }
    }

    private final AssessmentAttemptRepository attemptRepository;
    private final QuizRepository quizRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssessmentFileRepository fileRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AssessmentScheduleService(AssessmentAttemptRepository attemptRepository, QuizRepository quizRepository,
                                     AssignmentRepository assignmentRepository, AssessmentFileRepository fileRepository,
                                     CourseRepository courseRepository, EnrollmentRepository enrollmentRepository,
                                     SubmissionRepository submissionRepository, UserRepository userRepository,
                                     NotificationService notificationService) {
        this.attemptRepository = attemptRepository;
        this.quizRepository = quizRepository;
        this.assignmentRepository = assignmentRepository;
        this.fileRepository = fileRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ── Règles ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Rules rules(String kind, Long id) {
        String k = normalizeKind(kind);
        return switch (k) {
            case AssessmentAttempt.QUIZ -> {
                Quiz q = quizRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Quiz introuvable."));
                yield new Rules(k, id, q.getTitle(), courseOf(q.getChapter(), q.getLesson()), q.getDueAt(), q.getDurationMinutes());
            }
            case AssessmentAttempt.TP -> {
                Assignment a = assignmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("TP introuvable."));
                yield new Rules(k, id, a.getTitle(), courseOf(a.getChapter(), a.getLesson()), a.getDueAt(), a.getDurationMinutes());
            }
            default -> {
                AssessmentFile f = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Sujet introuvable."));
                Course c = courseRepository.findById(f.getCourseId()).orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
                yield new Rules(k, id, f.getTitle(), c, f.getDueAt(), f.getDurationMinutes());
            }
        };
    }

    /** Enseignant du cours (ou admin) : fixe la date limite et la durée, et prévient les étudiants. */
    public Map<String, Object> update(String email, String kind, Long id, Instant dueAt, Integer durationMinutes) {
        User user = findUser(email);
        Rules current = rules(kind, id);
        boolean admin = "ADMIN".equals(roleName(user)) || "SUPER_ADMIN".equals(roleName(user));
        if (!admin && (current.course().getTeacher() == null || !current.course().getTeacher().getId().equals(user.getId()))) {
            throw new SecurityException("Ce quiz / TP appartient au cours d'un autre enseignant.");
        }
        validate(dueAt, durationMinutes, current.dueAt());
        apply(current.kind(), id, dueAt, durationMinutes);
        if (dueAt != null && !dueAt.equals(current.dueAt())) {
            notifyStudents(current.course(), List.of(current.title()), dueAt, durationMinutes);
        }
        return describe(rules(kind, id));
    }

    /** Après une génération par l'IA : mêmes règles pour tous les quiz / TP produits, une seule notification. */
    public void applyAfterGeneration(Course course, List<Quiz> quizzes, List<Assignment> assignments, Instant dueAt,
                                     Integer quizDuration, Integer assignmentDuration) {
        if (dueAt == null && quizDuration == null && assignmentDuration == null) return;
        validate(dueAt, quizDuration, null);
        validate(dueAt, assignmentDuration, null);
        quizzes.forEach(q -> apply(AssessmentAttempt.QUIZ, q.getId(), dueAt, quizDuration));
        assignments.forEach(a -> apply(AssessmentAttempt.TP, a.getId(), dueAt, assignmentDuration));
        if (dueAt != null && course != null && (!quizzes.isEmpty() || !assignments.isEmpty())) {
            List<String> titles = new ArrayList<>();
            quizzes.forEach(q -> titles.add(q.getTitle()));
            assignments.forEach(a -> titles.add(a.getTitle()));
            notifyStudents(course, titles, dueAt, null);
        }
    }

    /** Vérifie et applique les règles d'un sujet importé (l'appelant enregistre le sujet). */
    public void validateForFile(Instant dueAt, Integer durationMinutes) {
        validate(dueAt, durationMinutes, null);
    }

    public void notifyFilePublished(Course course, String title, Instant dueAt, Integer durationMinutes) {
        if (dueAt != null) notifyStudents(course, List.of(title), dueAt, durationMinutes);
    }

    private void validate(Instant dueAt, Integer durationMinutes, Instant previousDueAt) {
        if (durationMinutes != null && (durationMinutes < 1 || durationMinutes > 24 * 60)) {
            throw new IllegalArgumentException("La durée doit être comprise entre 1 minute et 24 heures.");
        }
        if (dueAt != null && !dueAt.equals(previousDueAt) && dueAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("La date limite doit être dans le futur.");
        }
    }

    private void apply(String kind, Long id, Instant dueAt, Integer durationMinutes) {
        switch (kind) {
            case AssessmentAttempt.QUIZ -> quizRepository.findById(id).ifPresent(q -> {
                q.setDueAt(dueAt);
                q.setDurationMinutes(durationMinutes);
                if (durationMinutes != null) q.setTimeLimitMinutes(durationMinutes);
            });
            case AssessmentAttempt.TP -> assignmentRepository.findById(id).ifPresent(a -> {
                a.setDueAt(dueAt);
                a.setDurationMinutes(durationMinutes);
            });
            default -> fileRepository.findById(id).ifPresent(f -> {
                f.setDueAt(dueAt);
                f.setDurationMinutes(durationMinutes);
            });
        }
    }

    private void notifyStudents(Course course, List<String> titles, Instant dueAt, Integer durationMinutes) {
        String what = titles.size() == 1 ? "« " + titles.get(0) + " »" : titles.size() + " quiz / TP";
        String duration = durationMinutes != null ? " · durée " + formatDuration(durationMinutes) : "";
        String message = what + " du cours « " + course.getTitle() + " » à rendre avant le " + DATE.format(dueAt) + duration + ".";
        for (Enrollment e : enrollmentRepository.findByCourseId(course.getId())) {
            notificationService.sendNotification(e.getStudent(), "Date limite de rendu", message, "ASSESSMENT_DEADLINE", course.getId());
        }
    }

    // ── Passage de l'étudiant ────────────────────────────────────

    /** État pour l'étudiant (sans démarrer le chrono). */
    public Map<String, Object> status(String email, String kind, Long id) {
        User student = findUser(email);
        Rules r = rules(kind, id);
        requireEnrolled(student, r);
        return view(r, attempt(student, r).orElse(null), student);
    }

    /** « Commencer » : crée le passage (une seule fois) et renvoie l'heure de fin fixée par le serveur. */
    public Map<String, Object> start(String email, String kind, Long id) {
        User student = findUser(email);
        Rules r = rules(kind, id);
        requireEnrolled(student, r);
        Optional<AssessmentAttempt> existing = attempt(student, r);
        if (existing.isPresent() || !r.timed()) return view(r, existing.orElse(null), student);
        // À la seconde près : même valeur avant et après l'enregistrement en base
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if (r.dueAt() != null && now.isAfter(r.dueAt())) {
            throw new IllegalStateException("La date limite de rendu est dépassée.");
        }
        Instant ends = now.plusSeconds(r.durationMinutes() * 60L);
        if (r.dueAt() != null && r.dueAt().isBefore(ends)) ends = r.dueAt();
        AssessmentAttempt created = attemptRepository.save(new AssessmentAttempt(student, r.kind(), id, now, ends));
        return view(r, created, student);
    }

    /** Brouillon des réponses (gardé pour l'envoi automatique à la fin du temps). */
    public Map<String, Object> saveDraft(String email, String kind, Long id, String draft) {
        User student = findUser(email);
        Rules r = rules(kind, id);
        AssessmentAttempt a = attempt(student, r).orElseThrow(() -> new IllegalStateException("Commence d'abord le quiz / TP."));
        if (a.getSubmittedAt() != null) throw new IllegalStateException("Ta copie a déjà été rendue.");
        if (Instant.now().isAfter(a.getEndsAt().plusSeconds(GRACE_SECONDS))) throw new IllegalStateException("Le temps est écoulé.");
        if (draft != null && draft.length() > MAX_DRAFT) throw new IllegalArgumentException("Réponse trop longue.");
        a.setDraft(draft);
        return Map.of("savedAt", Instant.now());
    }

    /** Minuté et pas encore commencé : le contenu (questions, énoncé, sujet) reste caché. */
    @Transactional(readOnly = true)
    public void requireStartedIfTimed(User student, String kind, Long id) {
        if (!"STUDENT".equals(roleName(student))) return;
        Rules r = rules(kind, id);
        if (r.timed() && attempt(student, r).isEmpty()) {
            throw new IllegalStateException("Clique sur « Commencer » pour lancer le chrono et voir le sujet.");
        }
    }

    /**
     * Avant d'enregistrer une copie (quiz ou TP de l'IA) : refuse après la date limite ; pour un passage minuté
     * dont le temps est écoulé, les réponses envoyées en retard sont ignorées et le brouillon est utilisé.
     * Renvoie le contenu à corriger.
     */
    public String beginSubmission(User student, String kind, Long id, String content) {
        Rules r = rules(kind, id);
        Instant now = Instant.now();
        if (!r.timed()) {
            if (r.dueAt() != null && now.isAfter(r.dueAt().plusSeconds(GRACE_SECONDS))) {
                throw new IllegalStateException("La date limite de rendu est dépassée.");
            }
            return content;
        }
        AssessmentAttempt a = attempt(student, r).orElseThrow(() -> new IllegalStateException("Commence d'abord le quiz / TP."));
        if (a.getSubmittedAt() != null) throw new IllegalStateException("Ta copie a déjà été rendue (un seul passage).");
        if (now.isAfter(a.getEndsAt().plusSeconds(GRACE_SECONDS))) {
            return a.getDraft() != null ? a.getDraft() : content;
        }
        return content;
    }

    public void markSubmitted(Long studentId, String kind, Long id, boolean automatic) {
        attemptRepository.findByStudentIdAndKindAndAssessmentId(studentId, normalizeKind(kind), id).ifPresent(a -> {
            if (a.getSubmittedAt() != null) return;
            a.setSubmittedAt(Instant.now());
            a.setAutoSubmitted(automatic);
        });
    }

    /** Sujet importé : la copie peut être déposée (ou remplacée) jusqu'à la fin du temps / la date limite. */
    public void checkFileUpload(User student, AssessmentFile f) {
        Rules r = new Rules(AssessmentAttempt.FILE, f.getId(), f.getTitle(), null, f.getDueAt(), f.getDurationMinutes());
        Instant now = Instant.now();
        if (r.dueAt() != null && now.isAfter(r.dueAt().plusSeconds(GRACE_SECONDS))) {
            throw new IllegalStateException("La date limite de rendu est dépassée.");
        }
        if (r.timed()) {
            AssessmentAttempt a = attempt(student, r).orElseThrow(() -> new IllegalStateException("Commence d'abord le sujet."));
            if (now.isAfter(a.getEndsAt().plusSeconds(GRACE_SECONDS))) throw new IllegalStateException("Le temps est écoulé.");
            if (a.getSubmittedAt() == null) a.setSubmittedAt(now);
        }
    }

    /** Résumé pour les listes (cours de l'étudiant) : règles + état de son passage. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(User student, String kind, Long id, Instant dueAt, Integer durationMinutes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dueAt", dueAt);
        m.put("durationMinutes", durationMinutes);
        Instant now = Instant.now();
        m.put("closed", dueAt != null && now.isAfter(dueAt));
        attemptRepository.findByStudentIdAndKindAndAssessmentId(student.getId(), kind, id).ifPresent(a -> {
            m.put("startedAt", a.getStartedAt());
            m.put("endsAt", a.getEndsAt());
            m.put("submitted", a.getSubmittedAt() != null);
            m.put("autoSubmitted", Boolean.TRUE.equals(a.getAutoSubmitted()));
            m.put("expired", now.isAfter(a.getEndsAt()));
        });
        return m;
    }

    public Map<String, Object> describe(Rules r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", r.kind());
        m.put("id", r.id());
        m.put("dueAt", r.dueAt());
        m.put("durationMinutes", r.durationMinutes());
        return m;
    }

    private Map<String, Object> view(Rules r, AssessmentAttempt a, User student) {
        Instant now = Instant.now();
        Map<String, Object> m = describe(r);
        m.put("title", r.title());
        m.put("courseId", r.course().getId());
        m.put("timed", r.timed());
        m.put("serverNow", now);
        m.put("closed", r.dueAt() != null && now.isAfter(r.dueAt()));
        m.put("started", a != null);
        if (a != null) {
            m.put("startedAt", a.getStartedAt());
            m.put("endsAt", a.getEndsAt());
            m.put("remainingSeconds", Math.max(0, a.getEndsAt().getEpochSecond() - now.getEpochSecond()));
            m.put("expired", now.isAfter(a.getEndsAt()));
            m.put("submitted", a.getSubmittedAt() != null);
            m.put("autoSubmitted", Boolean.TRUE.equals(a.getAutoSubmitted()));
            m.put("draft", a.getSubmittedAt() == null ? a.getDraft() : null);
        } else {
            m.put("submitted", false);
        }
        // Quiz déjà rendu : dernier résultat (note et correction) pour l'afficher sans repasser le quiz
        if (AssessmentAttempt.QUIZ.equals(r.kind())) {
            submissionRepository.findByStudentIdAndQuizId(student.getId(), r.id()).stream()
                    .max(Comparator.comparing(Submission::getSubmittedAt))
                    .ifPresent(s -> {
                        Map<String, Object> last = new LinkedHashMap<>();
                        last.put("score", s.getPoints());
                        last.put("maxScore", s.getMaxPoints());
                        last.put("feedback", s.getFeedback());
                        last.put("detailedExplanation", s.getDetailedExplanation());
                        last.put("submittedAt", s.getSubmittedAt());
                        m.put("lastResult", last);
                    });
        }
        return m;
    }

    // ── Outils ───────────────────────────────────────────────────

    private Optional<AssessmentAttempt> attempt(User student, Rules r) {
        return attemptRepository.findByStudentIdAndKindAndAssessmentId(student.getId(), r.kind(), r.id());
    }

    private void requireEnrolled(User student, Rules r) {
        if (enrollmentRepository.findByStudentIdAndCourseId(student.getId(), r.course().getId()).isEmpty()) {
            throw new SecurityException("Inscris-toi au cours pour accéder à ce quiz / TP.");
        }
    }

    private static Course courseOf(Chapter chapter, Lesson lesson) {
        if (chapter != null) return chapter.getCourse();
        if (lesson != null) return lesson.getChapter().getCourse();
        throw new IllegalArgumentException("Quiz / TP sans chapitre.");
    }

    static String normalizeKind(String kind) {
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        return switch (k) {
            case "QUIZ" -> AssessmentAttempt.QUIZ;
            case "TP", "ASSIGNMENT" -> AssessmentAttempt.TP;
            case "FILE" -> AssessmentAttempt.FILE;
            default -> throw new IllegalArgumentException("Type inconnu : " + kind);
        };
    }

    static String formatDuration(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
    }

    private static String roleName(User user) {
        return user.getRole() != null ? user.getRole().getName() : "";
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
    }
}
