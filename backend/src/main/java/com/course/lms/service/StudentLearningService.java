package com.course.lms.service;

import com.course.lms.dto.ChapterResponse;
import com.course.lms.dto.CourseResponse;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Espace étudiant : lecture d'un cours (gratuit ou acheté), progression par leçon,
 * TP avec correction IA, statistiques et chatbot du cours (LMS_AI / Azure).
 */
@Service
@Transactional
public class StudentLearningService {

    private final WebClient webClient;

    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private LessonProgressRepository lessonProgressRepository;
    @Autowired private CourseService courseService;
    @Autowired private CourseProgressService progressService;
    @Autowired private AssessmentFileService assessmentFileService;
    @Autowired private LessonResourceRepository lessonResourceRepository;
    @Autowired private AssessmentFileRepository assessmentFileRepository;
    @Autowired private AssessmentFileSubmissionRepository assessmentFileSubmissionRepository;
    @Autowired private AssessmentScheduleService scheduleService;

    @Autowired
    public StudentLearningService(@Qualifier("lmsAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    // ── Lecture du cours ─────────────────────────────────────────

    /** Contenu complet du cours + leçons terminées + quiz/TP de chaque chapitre. */
    public Map<String, Object> getLearningView(Long courseId, String email) {
        User student = findStudent(email);
        Enrollment enrollment = requireEnrollment(student, courseId);
        enrollment.setLastAccessedAt(LocalDateTime.now());

        CourseResponse course = courseService.getCourseById(courseId);
        List<Map<String, Object>> chapterAssessments = new ArrayList<>();
        for (ChapterResponse chapter : safe(course.getChapters())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapterId", chapter.getId());
            item.put("quizzes", quizRepository.findByChapterId(chapter.getId()).stream().map(q -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", q.getId());
                m.put("title", q.getTitle());
                m.put("difficultyLevel", q.getDifficultyLevel().toString());
                m.put("questionCount", q.getQuestionCount());
                m.put("bestScore", bestScore(submissionRepository.findByStudentIdAndQuizId(student.getId(), q.getId())));
                m.put("schedule", scheduleService.summary(student, AssessmentAttempt.QUIZ, q.getId(), q.getDueAt(), q.getDurationMinutes()));
                return m;
            }).toList());
            item.put("assignments", assignmentRepository.findByChapterId(chapter.getId()).stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("title", a.getTitle());
                m.put("difficultyLevel", a.getDifficultyLevel().toString());
                m.put("exerciseCount", a.getExerciseCount());
                m.put("bestScore", bestScore(submissionRepository.findByStudentIdAndAssignmentId(student.getId(), a.getId())));
                m.put("schedule", scheduleService.summary(student, AssessmentAttempt.TP, a.getId(), a.getDueAt(), a.getDurationMinutes()));
                return m;
            }).toList());
            chapterAssessments.add(item);
        }

        Course entity = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("Cours non trouvé"));
        CourseProgressService.Snapshot progress = progressService.refresh(student, entity, enrollment);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("course", course);
        view.put("enrollmentId", enrollment.getId());
        view.put("progressPercentage", progress.coursePercent());
        view.put("completedLessonIds", new ArrayList<>(progress.completedLessons()));
        view.put("lessonProgress", progress.lessons());
        view.put("resourceProgress", progress.resources());
        view.put("assessments", chapterAssessments);
        view.put("files", assessmentFileService.courseFiles(student, courseId));
        view.put("serverNow", java.time.Instant.now());
        return view;
    }

    /** Marque une leçon comme terminée et recalcule la progression du cours. */
    public Map<String, Object> completeLesson(Long lessonId, String email) {
        User student = findStudent(email);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Leçon non trouvée"));
        Course course = lesson.getChapter().getCourse();
        Enrollment enrollment = requireEnrollment(student, course.getId());

        if (!lessonProgressRepository.existsByStudentIdAndLessonId(student.getId(), lessonId)) {
            lessonProgressRepository.save(new LessonProgress(student, lesson, course.getId()));
        }

        return progressResult(progressService.refresh(student, course, enrollment), enrollment);
    }

    /** Avancement mesuré d'une ressource (pages PDF vues, part de vidéo / audio, lien ouvert). */
    public Map<String, Object> recordResourceProgress(Long resourceId, Integer percent, String email) {
        User student = findStudent(email);
        LessonResource resource = lessonResourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Ressource introuvable"));
        Course course = resource.getLesson().getChapter().getCourse();
        Enrollment enrollment = requireEnrollment(student, course.getId());
        if (percent == null) throw new IllegalArgumentException("Pourcentage manquant");
        return progressResult(progressService.recordResource(student, resource, enrollment, percent), enrollment);
    }

    private Map<String, Object> progressResult(CourseProgressService.Snapshot progress, Enrollment enrollment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("progressPercentage", progress.coursePercent());
        result.put("completedLessonIds", new ArrayList<>(progress.completedLessons()));
        result.put("lessonProgress", progress.lessons());
        result.put("resourceProgress", progress.resources());
        result.put("isCompleted", Boolean.TRUE.equals(enrollment.getIsCompleted()));
        return result;
    }

    // ── TP ───────────────────────────────────────────────────────

    /** Énoncé du TP ; la solution n'est donnée qu'après une soumission. */
    public Map<String, Object> getAssignment(Long assignmentId, String email) {
        User student = findStudent(email);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("TP non trouvé"));
        Course course = assignment.getLesson().getChapter().getCourse();
        requireEnrollment(student, course.getId());
        // TP minuté : l'énoncé n'est visible qu'une fois le chrono lancé
        scheduleService.requireStartedIfTimed(student, AssessmentAttempt.TP, assignmentId);

        Map<String, Object> view = assignmentView(assignment, course);
        submissionRepository.findByStudentIdAndAssignmentId(student.getId(), assignmentId).stream()
                .max(Comparator.comparing(Submission::getSubmittedAt))
                .ifPresent(last -> view.put("lastSubmission", submissionView(last, assignment)));
        return view;
    }

    /** Soumet la réponse de l'étudiant, la fait corriger par l'IA et renvoie la correction complète. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitAssignment(Long assignmentId, String answer, String email) {
        User student = findStudent(email);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("TP non trouvé"));
        Course course = assignment.getLesson().getChapter().getCourse();
        requireEnrollment(student, course.getId());
        // Date limite / chrono : après la fin du temps, c'est la réponse enregistrée qui est corrigée
        String effective = scheduleService.beginSubmission(student, AssessmentAttempt.TP, assignmentId, answer);
        if (effective == null || effective.isBlank()) {
            throw new IllegalArgumentException("La réponse est vide.");
        }
        Map<String, Object> result = correctAndSave(assignment, student, effective);
        scheduleService.markSubmitted(student.getId(), AssessmentAttempt.TP, assignmentId, false);
        return result;
    }

    /** Fin du temps sans validation : la réponse enregistrée (même vide) est corrigée et notée. */
    public Map<String, Object> autoSubmitAssignment(Long assignmentId, User student, String draft) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("TP non trouvé"));
        String answer = draft == null || draft.isBlank() ? "(Aucune réponse rendue avant la fin du temps)" : draft;
        return correctAndSave(assignment, student, answer);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> correctAndSave(Assignment assignment, User student, String answer) {

        String statement = notBlank(assignment.getDescription()) ? assignment.getDescription() : assignment.getTitle();
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", statement);
        payload.put("student_answer", answer);
        payload.put("expected_answer", notBlank(assignment.getExpectedSolution()) ? assignment.getExpectedSolution() : "");
        // Énoncé avec des blocs de code : correction orientée code
        payload.put("response_type", statement.contains("```") ? "code" : "text");
        payload.put("level", assignment.getDifficultyLevel().toString().toLowerCase());

        Map<String, Object> ai = webClient.post()
                .uri("/api/correct/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (ai == null) {
            throw new RuntimeException("Aucune réponse du service IA.");
        }

        double score = ai.get("score") instanceof Number n ? n.doubleValue() : 0.0;
        double maxPoints = assignment.getMaxPoints() != null ? assignment.getMaxPoints() : 100.0;
        Submission submission = new Submission(assignment, student, answer);
        // Le score IA est sur 100 : ramené au barème du TP
        submission.setPoints(Math.round(score * maxPoints) / 100.0);
        submission.setMaxPoints(maxPoints);
        submission.setFeedback((String) ai.get("feedback"));
        submission.setDetailedExplanation((String) ai.get("correction"));
        submission.setGradedByAi(true);
        submission.setGradedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        Map<String, Object> result = submissionView(submission, assignment);
        result.put("errors", ai.getOrDefault("errors", List.of()));
        return result;
    }

    // ── Statistiques ─────────────────────────────────────────────

    public Map<String, Object> getStatistics(String email) {
        User student = findStudent(email);
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());
        List<Submission> submissions = submissionRepository.findByStudentId(student.getId());

        int totalLessons = 0;
        long lessonsCompleted = 0;
        List<Map<String, Object>> courses = new ArrayList<>();
        long quizzesTotal = 0, quizzesDone = 0, tpsTotal = 0, tpsDone = 0;
        Set<Long> doneQuizIds = new HashSet<>();
        Set<Long> doneAssignmentIds = new HashSet<>();
        for (Submission s : submissions) {
            if (s.getQuiz() != null) doneQuizIds.add(s.getQuiz().getId());
            if (s.getAssignment() != null) doneAssignmentIds.add(s.getAssignment().getId());
        }
        Set<Long> doneFileIds = new HashSet<>();
        for (AssessmentFileSubmission s : assessmentFileSubmissionRepository.findByStudentId(student.getId())) {
            doneFileIds.add(s.getAssessmentFile().getId());
        }
        for (Enrollment e : enrollments) {
            Course course = e.getCourse();
            progressService.refresh(student, course, e);
            int courseLessons = countLessons(course);
            long cq = 0, cqDone = 0, ct = 0, ctDone = 0;
            for (Chapter ch : course.getChapters()) {
                for (Quiz q : quizRepository.findByChapterId(ch.getId())) { cq++; if (doneQuizIds.contains(q.getId())) cqDone++; }
                for (Assignment a : assignmentRepository.findByChapterId(ch.getId())) { ct++; if (doneAssignmentIds.contains(a.getId())) ctDone++; }
            }
            for (AssessmentFile f : assessmentFileRepository.findByCourseIdOrderByCreatedAtAscIdAsc(course.getId())) {
                boolean done = doneFileIds.contains(f.getId());
                if (AssessmentFile.QUIZ.equals(f.getType())) { cq++; if (done) cqDone++; } else { ct++; if (done) ctDone++; }
            }
            quizzesTotal += cq; quizzesDone += cqDone; tpsTotal += ct; tpsDone += ctDone;
            long done = lessonProgressRepository.countByStudentIdAndCourseId(student.getId(), course.getId());
            totalLessons += courseLessons;
            lessonsCompleted += done;

            List<Submission> courseSubs = submissions.stream().filter(s -> course.getId().equals(courseIdOf(s))).toList();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("courseId", course.getId());
            c.put("title", course.getTitle());
            c.put("pricingType", course.getPricingType());
            c.put("progressPercentage", e.getProgressPercentage() != null ? e.getProgressPercentage() : 0);
            c.put("isCompleted", Boolean.TRUE.equals(e.getIsCompleted()));
            c.put("lessonsCompleted", done);
            c.put("totalLessons", courseLessons);
            c.put("quizzesTotal", cq);
            c.put("quizzesDone", cqDone);
            c.put("tpsTotal", ct);
            c.put("tpsDone", ctDone);
            c.put("quizAverage", averagePercent(courseSubs.stream().filter(s -> s.getQuiz() != null).toList()));
            c.put("assignmentAverage", averagePercent(courseSubs.stream().filter(s -> s.getAssignment() != null).toList()));
            c.put("enrolledAt", e.getEnrolledAt());
            c.put("lastAccessedAt", e.getLastAccessedAt());
            courses.add(c);
        }

        List<Map<String, Object>> recent = submissions.stream()
                .filter(s -> s.getPoints() != null)
                .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed())
                .limit(10)
                .map(s -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("type", s.getQuiz() != null ? "QUIZ" : "TP");
                    r.put("title", s.getQuiz() != null ? s.getQuiz().getTitle() : s.getAssignment().getTitle());
                    r.put("courseTitle", courseTitleOf(s));
                    r.put("percent", percent(s));
                    r.put("submittedAt", s.getSubmittedAt());
                    return r;
                }).toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("coursesEnrolled", enrollments.size());
        stats.put("coursesCompleted", enrollments.stream().filter(e -> Boolean.TRUE.equals(e.getIsCompleted())).count());
        stats.put("lessonsCompleted", lessonsCompleted);
        stats.put("totalLessons", totalLessons);
        stats.put("averageProgress", enrollments.isEmpty() ? 0 : Math.round(enrollments.stream()
                .mapToInt(e -> e.getProgressPercentage() != null ? e.getProgressPercentage() : 0).average().orElse(0)));
        stats.put("quizzesTaken", submissions.stream().filter(s -> s.getQuiz() != null).count());
        stats.put("assignmentsSubmitted", submissions.stream().filter(s -> s.getAssignment() != null).count());
        stats.put("quizAverage", averagePercent(submissions.stream().filter(s -> s.getQuiz() != null).toList()));
        stats.put("assignmentAverage", averagePercent(submissions.stream().filter(s -> s.getAssignment() != null).toList()));
        stats.put("quizzesTotal", quizzesTotal);
        stats.put("quizzesDone", quizzesDone);
        stats.put("tpsTotal", tpsTotal);
        stats.put("tpsDone", tpsDone);
        stats.put("courses", courses);
        stats.put("recentResults", recent);
        return stats;
    }

    // ── Chatbot du cours ─────────────────────────────────────────

    /**
     * Chatbot IA du cours : l'étudiant pose des questions ou demande un résumé ;
     * le contenu du cours (ou de la leçon ouverte) est envoyé à LMS_AI.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> courseChat(Long courseId, Map<String, Object> request, String email) {
        User student = findStudent(email);
        requireEnrollment(student, courseId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        Long lessonId = request.get("lessonId") instanceof Number n ? n.longValue() : null;
        List<Lesson> lessons = new ArrayList<>();
        String lessonTitle = null;
        if (lessonId != null) {
            Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
            if (lesson != null && lesson.getChapter().getCourse().getId().equals(courseId)) {
                lessons.add(lesson);
                lessonTitle = lesson.getTitle();
            }
        }
        if (lessons.isEmpty()) {
            course.getChapters().forEach(ch -> lessons.addAll(ch.getLessons()));
        }

        List<String> descriptions = new ArrayList<>();
        if (notBlank(course.getDescription())) {
            descriptions.add("Cours : " + course.getTitle() + ". " + course.getDescription());
        }
        List<Map<String, String>> resources = new ArrayList<>();
        for (Lesson lesson : lessons) {
            descriptions.add("Leçon : " + lesson.getTitle() + (notBlank(lesson.getDescription()) ? ". " + lesson.getDescription() : ""));
            for (LessonResource r : lesson.getResources()) {
                Map<String, String> d = new HashMap<>();
                d.put("type", r.getResourceType().toString());
                if (r.getFilePath() != null) d.put("file_path", r.getFilePath());
                if (r.getExternalUrl() != null) d.put("external_url", r.getExternalUrl());
                d.put("title", r.getTitle() != null ? r.getTitle() : "");
                resources.add(d);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("course_title", course.getTitle());
        payload.put("lesson_title", lessonTitle);
        payload.put("lesson_descriptions", descriptions);
        payload.put("resource_details", resources);
        payload.put("messages", request.getOrDefault("messages", List.of()));
        payload.put("action", "summary".equals(request.get("action")) ? "summary" : "chat");

        Map<String, Object> ai = webClient.post()
                .uri("/api/chat/course")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return Map.of("answer", ai != null ? ai.getOrDefault("answer", "") : "");
    }

    // ── Utilitaires ──────────────────────────────────────────────

    private User findStudent(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    /** Un cours (gratuit ou payant) n'est lisible qu'après inscription / achat. */
    private Enrollment requireEnrollment(User student, Long courseId) {
        return enrollmentRepository.findByStudentIdAndCourseId(student.getId(), courseId)
                .orElseThrow(() -> new SecurityException("Inscrivez-vous à ce cours (ou achetez-le) pour y accéder."));
    }

    private Map<String, Object> assignmentView(Assignment a, Course course) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("description", a.getDescription());
        m.put("difficultyLevel", a.getDifficultyLevel().toString());
        m.put("exerciseCount", a.getExerciseCount());
        m.put("maxPoints", a.getMaxPoints());
        m.put("courseId", course.getId());
        m.put("courseTitle", course.getTitle());
        return m;
    }

    private Map<String, Object> submissionView(Submission s, Assignment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", s.getContent());
        m.put("points", s.getPoints());
        m.put("maxPoints", s.getMaxPoints());
        m.put("percent", percent(s));
        m.put("feedback", s.getFeedback());
        m.put("aiCorrection", s.getDetailedExplanation());
        // Solution du professeur / de l'IA, visible après soumission
        m.put("expectedSolution", a.getExpectedSolution());
        m.put("submittedAt", s.getSubmittedAt());
        return m;
    }

    private int countLessons(Course course) {
        return course.getChapters().stream().mapToInt(ch -> ch.getLessons().size()).sum();
    }

    private Long courseIdOf(Submission s) {
        Lesson lesson = s.getQuiz() != null ? s.getQuiz().getLesson() : s.getAssignment() != null ? s.getAssignment().getLesson() : null;
        return lesson != null ? lesson.getChapter().getCourse().getId() : null;
    }

    private String courseTitleOf(Submission s) {
        Lesson lesson = s.getQuiz() != null ? s.getQuiz().getLesson() : s.getAssignment().getLesson();
        return lesson != null ? lesson.getChapter().getCourse().getTitle() : "";
    }

    private static Double percent(Submission s) {
        if (s.getPoints() == null) return null;
        double max = s.getMaxPoints() != null && s.getMaxPoints() > 0 ? s.getMaxPoints() : 100.0;
        return Math.round(s.getPoints() * 1000.0 / max) / 10.0;
    }

    private static Double bestScore(List<Submission> submissions) {
        return submissions.stream().map(StudentLearningService::percent).filter(Objects::nonNull)
                .max(Double::compare).orElse(null);
    }

    private static Double averagePercent(List<Submission> submissions) {
        OptionalDouble avg = submissions.stream().map(StudentLearningService::percent).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }
}
