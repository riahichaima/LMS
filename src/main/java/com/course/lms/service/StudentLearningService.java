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
                return m;
            }).toList());
            item.put("assignments", assignmentRepository.findByChapterId(chapter.getId()).stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("title", a.getTitle());
                m.put("difficultyLevel", a.getDifficultyLevel().toString());
                m.put("exerciseCount", a.getExerciseCount());
                m.put("bestScore", bestScore(submissionRepository.findByStudentIdAndAssignmentId(student.getId(), a.getId())));
                return m;
            }).toList());
            chapterAssessments.add(item);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("course", course);
        view.put("enrollmentId", enrollment.getId());
        view.put("progressPercentage", enrollment.getProgressPercentage());
        view.put("completedLessonIds", lessonProgressRepository.findCompletedLessonIds(student.getId(), courseId));
        view.put("assessments", chapterAssessments);
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

        int total = countLessons(course);
        long done = lessonProgressRepository.countByStudentIdAndCourseId(student.getId(), course.getId());
        int percentage = total == 0 ? 0 : (int) Math.min(100, Math.round(done * 100.0 / total));
        enrollment.setProgressPercentage(percentage);
        enrollment.setLastAccessedAt(LocalDateTime.now());
        if (percentage >= 100 && !Boolean.TRUE.equals(enrollment.getIsCompleted())) {
            enrollment.setIsCompleted(true);
            enrollment.setCompletedAt(LocalDateTime.now());
        }
        enrollmentRepository.save(enrollment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("progressPercentage", percentage);
        result.put("completedLessonIds", lessonProgressRepository.findCompletedLessonIds(student.getId(), course.getId()));
        result.put("isCompleted", enrollment.getIsCompleted());
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

        Map<String, Object> view = assignmentView(assignment, course);
        submissionRepository.findByStudentIdAndAssignmentId(student.getId(), assignmentId).stream()
                .max(Comparator.comparing(Submission::getSubmittedAt))
                .ifPresent(last -> view.put("lastSubmission", submissionView(last, assignment)));
        return view;
    }

    /** Soumet la réponse de l'étudiant, la fait corriger par l'IA et renvoie la correction complète. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitAssignment(Long assignmentId, String answer, String email) {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("La réponse est vide.");
        }
        User student = findStudent(email);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("TP non trouvé"));
        Course course = assignment.getLesson().getChapter().getCourse();
        requireEnrollment(student, course.getId());

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
        for (Enrollment e : enrollments) {
            Course course = e.getCourse();
            int courseLessons = countLessons(course);
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
