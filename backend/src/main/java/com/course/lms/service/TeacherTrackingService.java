package com.course.lms.service;

import com.course.lms.dto.TrackingDtos.*;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Suivi pédagogique du formateur : progression des étudiants, notes (quiz / TP),
 * participation et export des résultats. Un formateur ne voit que ses propres cours
 * (les administrateurs peuvent consulter tous les cours).
 */
@Service
@Transactional(readOnly = true)
public class TeacherTrackingService {

    /** Seuil de réussite par défaut quand le quiz n'en définit pas (TP : toujours 50 %). */
    private static final double DEFAULT_PASS = 50.0;

    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LessonProgressRepository lessonProgressRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private SubmissionRepository submissionRepository;

    /** Évaluations d'un cours et leurs copies. */
    private record CourseData(Course course, int totalLessons, List<Enrollment> enrollments,
                              List<Quiz> quizzes, List<Assignment> assignments, List<Submission> submissions) {}

    // ── API ─────────────────────────────────────────────────────

    public Overview overview(String email) {
        User teacher = findUser(email);
        List<CourseData> data = courseRepository.findByTeacherId(teacher.getId()).stream()
                .sorted(Comparator.comparing(Course::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::load).toList();

        List<CourseSummary> courses = data.stream().map(d -> {
            Kpis k = kpis(List.of(d));
            return new CourseSummary(d.course().getId(), d.course().getTitle(), d.course().getPricingType(),
                    k.students(), k.averageProgress(), k.completed(), k.quizAverage(), k.assignmentAverage(),
                    k.submissions(), k.participationRate(), d.quizzes().size() + d.assignments().size());
        }).toList();

        List<AssessmentStats> assessments = data.stream().flatMap(d -> assessmentStats(d).stream())
                .sorted(Comparator.comparing(AssessmentStats::attempts).reversed()).toList();

        List<RecentSubmission> recent = data.stream()
                .flatMap(d -> d.submissions().stream().filter(s -> s.getPoints() != null).map(s -> Map.entry(d, s)))
                .sorted(Comparator.comparing((Map.Entry<CourseData, Submission> e) -> e.getValue().getSubmittedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(e -> new RecentSubmission(e.getValue().getQuiz() != null ? "QUIZ" : "TP", titleOf(e.getValue()),
                        e.getKey().course().getTitle(), e.getValue().getStudent().getName(), percent(e.getValue()),
                        e.getValue().getSubmittedAt()))
                .toList();

        return new Overview(kpis(data), courses, assessments, recent);
    }

    public CourseDetail courseDetail(Long courseId, String email, boolean isAdmin) {
        CourseData d = load(ownedCourse(courseId, email, isAdmin));
        return new CourseDetail(d.course().getId(), d.course().getTitle(), d.totalLessons(), kpis(List.of(d)),
                studentRows(d), assessmentStats(d));
    }

    /**
     * Export CSV (séparateur « ; », UTF-8 avec BOM pour Excel) : une ligne par étudiant,
     * avec la progression, les moyennes et le meilleur score de chaque quiz / TP.
     */
    public byte[] exportCsv(Long courseId, String email, boolean isAdmin) {
        CourseData d = load(ownedCourse(courseId, email, isAdmin));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<String> header = new ArrayList<>(List.of("Étudiant", "Email", "Inscrit le", "Dernier accès",
                "Progression (%)", "Leçons terminées", "Total leçons", "Cours terminé",
                "Quiz passés", "Moyenne quiz (%)", "TP rendus", "Moyenne TP (%)"));
        d.quizzes().forEach(q -> header.add("Quiz : " + q.getTitle() + " (%)"));
        d.assignments().forEach(a -> header.add("TP : " + a.getTitle() + " (%)"));

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Cours;").append(cell(d.course().getTitle())).append("\n");
        csv.append("Exporté le;").append(LocalDateTime.now().format(fmt)).append("\n\n");
        csv.append(header.stream().map(TeacherTrackingService::cell).collect(Collectors.joining(";"))).append("\n");

        Map<Long, List<Submission>> byStudent = d.submissions().stream()
                .collect(Collectors.groupingBy(s -> s.getStudent().getId()));
        for (StudentRow row : studentRows(d)) {
            List<Submission> subs = byStudent.getOrDefault(row.studentId(), List.of());
            List<String> cells = new ArrayList<>(List.of(
                    row.name(), row.email(),
                    row.enrolledAt() != null ? row.enrolledAt().format(fmt) : "",
                    row.lastAccessedAt() != null ? row.lastAccessedAt().format(fmt) : "",
                    String.valueOf(row.progress()), String.valueOf(row.lessonsCompleted()), String.valueOf(row.totalLessons()),
                    row.completed() ? "Oui" : "Non",
                    String.valueOf(row.quizzesTaken()), num(row.quizAverage()),
                    String.valueOf(row.assignmentsSubmitted()), num(row.assignmentAverage())));
            d.quizzes().forEach(q -> cells.add(num(best(subs.stream().filter(s -> s.getQuiz() != null && q.getId().equals(s.getQuiz().getId())).toList()))));
            d.assignments().forEach(a -> cells.add(num(best(subs.stream().filter(s -> s.getAssignment() != null && a.getId().equals(s.getAssignment().getId())).toList()))));
            csv.append(cells.stream().map(TeacherTrackingService::cell).collect(Collectors.joining(";"))).append("\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String exportFileName(Long courseId) {
        String title = courseRepository.findById(courseId).map(Course::getTitle).orElse("cours");
        String slug = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        return "resultats_" + (slug.isEmpty() ? "cours" : slug) + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
    }

    // ── Calculs ─────────────────────────────────────────────────

    private CourseData load(Course course) {
        Map<Long, Quiz> quizzes = new LinkedHashMap<>();
        Map<Long, Assignment> assignments = new LinkedHashMap<>();
        int totalLessons = 0;
        for (Chapter ch : course.getChapters()) {
            quizRepository.findByChapterId(ch.getId()).forEach(q -> quizzes.putIfAbsent(q.getId(), q));
            assignmentRepository.findByChapterId(ch.getId()).forEach(a -> assignments.putIfAbsent(a.getId(), a));
            for (Lesson lesson : ch.getLessons()) {
                totalLessons++;
                quizRepository.findByLessonId(lesson.getId()).forEach(q -> quizzes.putIfAbsent(q.getId(), q));
                assignmentRepository.findByLessonId(lesson.getId()).forEach(a -> assignments.putIfAbsent(a.getId(), a));
            }
        }
        List<Submission> submissions = new ArrayList<>();
        quizzes.keySet().forEach(id -> submissions.addAll(submissionRepository.findByQuizId(id)));
        assignments.keySet().forEach(id -> submissions.addAll(submissionRepository.findByAssignmentId(id)));
        return new CourseData(course, totalLessons, enrollmentRepository.findByCourseId(course.getId()),
                new ArrayList<>(quizzes.values()), new ArrayList<>(assignments.values()), submissions);
    }

    private List<StudentRow> studentRows(CourseData d) {
        Map<Long, List<Submission>> byStudent = d.submissions().stream()
                .collect(Collectors.groupingBy(s -> s.getStudent().getId()));
        return d.enrollments().stream().map(e -> {
            User st = e.getStudent();
            List<Submission> subs = byStudent.getOrDefault(st.getId(), List.of());
            List<Double> quizBests = bestPerAssessment(subs, true);
            List<Double> tpBests = bestPerAssessment(subs, false);
            long lessons = lessonProgressRepository.countByStudentIdAndCourseId(st.getId(), d.course().getId());
            return new StudentRow(st.getId(), st.getName(), st.getEmail(), e.getEnrolledAt(), e.getLastAccessedAt(),
                    progressOf(e), lessons, d.totalLessons(), Boolean.TRUE.equals(e.getIsCompleted()),
                    quizBests.size(), avg(quizBests), tpBests.size(), avg(tpBests), lessons > 0 || !subs.isEmpty());
        }).sorted(Comparator.comparing(StudentRow::progress).reversed()
                .thenComparing(r -> r.name() == null ? "" : r.name().toLowerCase())).toList();
    }

    private Kpis kpis(List<CourseData> data) {
        List<Enrollment> enrollments = data.stream().flatMap(d -> d.enrollments().stream()).toList();
        long students = enrollments.stream().map(e -> e.getStudent().getId()).distinct().count();
        long completed = enrollments.stream().filter(e -> Boolean.TRUE.equals(e.getIsCompleted())).count();
        int avgProgress = (int) Math.round(enrollments.stream().mapToInt(TeacherTrackingService::progressOf).average().orElse(0));
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long activeWeek = enrollments.stream().filter(e -> e.getLastAccessedAt() != null && e.getLastAccessedAt().isAfter(weekAgo))
                .map(e -> e.getStudent().getId()).distinct().count();

        List<Double> quizBests = new ArrayList<>();
        List<Double> tpBests = new ArrayList<>();
        long passed = 0, graded = 0, submissions = 0, active = 0;
        for (CourseData d : data) {
            submissions += d.submissions().size();
            Map<Long, List<Submission>> byStudent = d.submissions().stream().collect(Collectors.groupingBy(s -> s.getStudent().getId()));
            for (Enrollment e : d.enrollments()) {
                List<Submission> subs = byStudent.getOrDefault(e.getStudent().getId(), List.of());
                quizBests.addAll(bestPerAssessment(subs, true));
                tpBests.addAll(bestPerAssessment(subs, false));
                if (!subs.isEmpty() || lessonProgressRepository.countByStudentIdAndCourseId(e.getStudent().getId(), d.course().getId()) > 0) {
                    active++;
                }
            }
            for (AssessmentStats a : assessmentStats(d)) {
                if (a.passRate() != null) {
                    passed += Math.round(a.passRate() * a.students() / 100.0);
                    graded += a.students();
                }
            }
        }
        return new Kpis(data.size(), students, enrollments.size(), avgProgress,
                enrollments.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / enrollments.size()), completed,
                avg(quizBests), avg(tpBests), submissions,
                enrollments.isEmpty() ? 0 : (int) Math.round(active * 100.0 / enrollments.size()), activeWeek,
                graded == 0 ? null : Math.round(passed * 1000.0 / graded) / 10.0);
    }

    private List<AssessmentStats> assessmentStats(CourseData d) {
        List<AssessmentStats> out = new ArrayList<>();
        for (Quiz q : d.quizzes()) {
            List<Submission> subs = d.submissions().stream().filter(s -> s.getQuiz() != null && q.getId().equals(s.getQuiz().getId())).toList();
            double pass = q.getPassingScore() != null ? q.getPassingScore() : DEFAULT_PASS;
            out.add(stats(q.getId(), "QUIZ", q.getTitle(), d.course(), subs, pass));
        }
        for (Assignment a : d.assignments()) {
            List<Submission> subs = d.submissions().stream().filter(s -> s.getAssignment() != null && a.getId().equals(s.getAssignment().getId())).toList();
            out.add(stats(a.getId(), "TP", a.getTitle(), d.course(), subs, DEFAULT_PASS));
        }
        return out;
    }

    private AssessmentStats stats(Long id, String type, String title, Course course, List<Submission> subs, double pass) {
        Map<Long, Double> bestByStudent = new HashMap<>();
        for (Submission s : subs) {
            Double p = percent(s);
            if (p != null) bestByStudent.merge(s.getStudent().getId(), p, Math::max);
        }
        List<Double> bests = new ArrayList<>(bestByStudent.values());
        Double passRate = bests.isEmpty() ? null
                : Math.round(bests.stream().filter(b -> b >= pass).count() * 1000.0 / bests.size()) / 10.0;
        return new AssessmentStats(id, type, title, course.getId(), course.getTitle(), subs.size(),
                subs.stream().map(s -> s.getStudent().getId()).distinct().count(), avg(bests),
                bests.stream().max(Double::compare).orElse(null), passRate, pass);
    }

    /** Meilleur score de chaque évaluation passée (quiz ou TP). */
    private static List<Double> bestPerAssessment(List<Submission> subs, boolean quiz) {
        Map<Long, Double> best = new HashMap<>();
        for (Submission s : subs) {
            Long key = quiz ? (s.getQuiz() != null ? s.getQuiz().getId() : null) : (s.getAssignment() != null ? s.getAssignment().getId() : null);
            Double p = percent(s);
            if (key != null && p != null) best.merge(key, p, Math::max);
        }
        return new ArrayList<>(best.values());
    }

    private Course ownedCourse(Long courseId, String email, boolean isAdmin) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable"));
        if (!isAdmin && (course.getTeacher() == null || !course.getTeacher().getEmail().equals(email))) {
            throw new SecurityException("Vous ne pouvez consulter que le suivi de vos propres cours.");
        }
        return course;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
    }

    private static int progressOf(Enrollment e) {
        return e.getProgressPercentage() != null ? e.getProgressPercentage() : 0;
    }

    private static String titleOf(Submission s) {
        return s.getQuiz() != null ? s.getQuiz().getTitle() : s.getAssignment() != null ? s.getAssignment().getTitle() : "";
    }

    static Double percent(Submission s) {
        if (s.getPoints() == null) return null;
        double max = s.getMaxPoints() != null && s.getMaxPoints() > 0 ? s.getMaxPoints() : 100.0;
        return Math.round(s.getPoints() * 1000.0 / max) / 10.0;
    }

    private static Double best(List<Submission> subs) {
        return subs.stream().map(TeacherTrackingService::percent).filter(Objects::nonNull).max(Double::compare).orElse(null);
    }

    private static Double avg(List<Double> values) {
        return values.isEmpty() ? null : Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10.0) / 10.0;
    }

    private static String num(Double v) {
        return v == null ? "" : String.valueOf(v).replace('.', ',');
    }

    private static String cell(String v) {
        if (v == null) return "";
        // Neutralise les formules (injection CSV) et échappe les guillemets
        String safe = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return safe.contains(";") || safe.contains("\"") || safe.contains("\n") ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
