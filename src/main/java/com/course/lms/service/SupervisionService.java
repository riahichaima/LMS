package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Supervision (administrateur et super administrateur) : statistiques globales de la
 * plateforme et consultation du journal système.
 */
@Service
@Transactional(readOnly = true)
public class SupervisionService {

    private static final int SERIES_DAYS = 30;

    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private LessonProgressRepository lessonProgressRepository;
    @Autowired private SystemLogRepository logRepository;

    // ── Statistiques globales ───────────────────────────────────

    public Map<String, Object> globalStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.toLocalDate().minusDays(SERIES_DAYS - 1).atStartOfDay();

        List<User> users = userRepository.findAll();
        List<Course> courses = courseRepository.findAll();
        List<Enrollment> enrollments = enrollmentRepository.findAll();
        List<Submission> submissions = submissionRepository.findAll();
        List<Payment> paid = paymentRepository.findAll().stream().filter(p -> Payment.PAID.equals(p.getStatus())).toList();
        List<Message> messages = messageRepository.findAll();

        Map<String, Long> byRole = new LinkedHashMap<>();
        for (String role : List.of("STUDENT", "TEACHER", "ADMIN", "SUPER_ADMIN")) byRole.put(role, 0L);
        users.forEach(u -> byRole.merge(u.getRole() != null ? u.getRole().getName() : "?", 1L, Long::sum));

        Map<String, Object> usersBlock = new LinkedHashMap<>();
        usersBlock.put("total", users.size());
        usersBlock.put("byRole", byRole);
        usersBlock.put("enabled", users.stream().filter(User::isEnabled).count());
        usersBlock.put("disabled", users.stream().filter(u -> !u.isEnabled()).count());
        usersBlock.put("pendingAdmins", users.stream().filter(u -> ApprovalStatus.PENDING.equals(u.getApprovalStatus())).count());
        usersBlock.put("newLast30Days", users.stream().filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(since)).count());

        Map<String, Object> coursesBlock = new LinkedHashMap<>();
        coursesBlock.put("total", courses.size());
        coursesBlock.put("free", courses.stream().filter(c -> !c.isPaid()).count());
        coursesBlock.put("paid", courses.stream().filter(Course::isPaid).count());
        Set<Long> withEnrollments = enrollments.stream().map(e -> e.getCourse().getId()).collect(Collectors.toSet());
        coursesBlock.put("withoutStudents", courses.stream().filter(c -> !withEnrollments.contains(c.getId())).count());
        coursesBlock.put("teachers", courses.stream().filter(c -> c.getTeacher() != null).map(c -> c.getTeacher().getId()).distinct().count());

        long completed = enrollments.stream().filter(e -> Boolean.TRUE.equals(e.getIsCompleted())).count();
        Map<String, Object> learning = new LinkedHashMap<>();
        learning.put("enrollments", enrollments.size());
        learning.put("completed", completed);
        learning.put("completionRate", enrollments.isEmpty() ? 0 : Math.round(completed * 100.0 / enrollments.size()));
        learning.put("averageProgress", Math.round(enrollments.stream()
                .mapToInt(e -> e.getProgressPercentage() != null ? e.getProgressPercentage() : 0).average().orElse(0)));
        learning.put("lessonsCompleted", lessonProgressRepository.count());
        learning.put("quizSubmissions", submissions.stream().filter(s -> s.getQuiz() != null).count());
        learning.put("assignmentSubmissions", submissions.stream().filter(s -> s.getAssignment() != null).count());
        learning.put("quizAverage", avg(submissions.stream().filter(s -> s.getQuiz() != null).map(TeacherTrackingService::percent).toList()));
        learning.put("assignmentAverage", avg(submissions.stream().filter(s -> s.getAssignment() != null).map(TeacherTrackingService::percent).toList()));
        LocalDateTime weekAgo = now.minusDays(7);
        learning.put("activeStudents7Days", enrollments.stream()
                .filter(e -> e.getLastAccessedAt() != null && e.getLastAccessedAt().isAfter(weekAgo))
                .map(e -> e.getStudent().getId()).distinct().count());

        Map<String, Object> finance = new LinkedHashMap<>();
        finance.put("paidPayments", paid.size());
        finance.put("grossRevenue", sum(paid, Payment::getAmount));
        finance.put("platformCommission", sum(paid, Payment::getPlatformCommission));
        finance.put("currency", "usd");

        Map<String, Object> communication = new LinkedHashMap<>();
        communication.put("messages", messages.size());
        communication.put("messagesLast7Days", messages.stream().filter(m -> m.getSentAt() != null && m.getSentAt().isAfter(weekAgo)).count());

        LocalDateTime dayAgo = now.minusHours(24);
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("logsLast24h", logRepository.countByCreatedAtAfter(dayAgo));
        system.put("errorsLast24h", logRepository.countByLevelAndCreatedAtAfter(SystemLog.ERROR, dayAgo));
        system.put("securityLast24h", logRepository.countByLevelAndCreatedAtAfter(SystemLog.SECURITY, dayAgo));
        system.put("warningsLast24h", logRepository.countByLevelAndCreatedAtAfter(SystemLog.WARN, dayAgo));

        // Activité des 30 derniers jours (une valeur par jour)
        List<String> days = new ArrayList<>();
        for (int i = 0; i < SERIES_DAYS; i++) days.add(since.toLocalDate().plusDays(i).toString());
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("days", days);
        series.put("registrations", perDay(days, users.stream().map(User::getCreatedAt)));
        series.put("enrollments", perDay(days, enrollments.stream().map(Enrollment::getEnrolledAt)));
        series.put("submissions", perDay(days, submissions.stream().map(Submission::getSubmittedAt)));
        series.put("logins", perDay(days, logRepository.findDatesByActionSince("Connexion", since).stream()));
        series.put("payments", perDay(days, paid.stream().map(Payment::getPaidAt)));

        // Cours les plus suivis
        Map<Long, List<Enrollment>> byCourse = enrollments.stream().collect(Collectors.groupingBy(e -> e.getCourse().getId()));
        List<Map<String, Object>> topCourses = courses.stream()
                .sorted(Comparator.comparing((Course c) -> byCourse.getOrDefault(c.getId(), List.of()).size()).reversed())
                .limit(5)
                .map(c -> {
                    List<Enrollment> list = byCourse.getOrDefault(c.getId(), List.of());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId", c.getId());
                    m.put("title", c.getTitle());
                    m.put("teacher", c.getTeacher() != null ? c.getTeacher().getName() : null);
                    m.put("pricingType", c.getPricingType());
                    m.put("students", list.size());
                    m.put("averageProgress", Math.round(list.stream()
                            .mapToInt(e -> e.getProgressPercentage() != null ? e.getProgressPercentage() : 0).average().orElse(0)));
                    return m;
                }).toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("generatedAt", now);
        stats.put("users", usersBlock);
        stats.put("courses", coursesBlock);
        stats.put("learning", learning);
        stats.put("finance", finance);
        stats.put("communication", communication);
        stats.put("system", system);
        stats.put("series", series);
        stats.put("topCourses", topCourses);
        return stats;
    }

    // ── Journal système ─────────────────────────────────────────

    public Map<String, Object> searchLogs(String level, String category, String q, LocalDate from, LocalDate to, int page, int size) {
        Page<SystemLog> result = logRepository.findAll(spec(level, category, q, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", result.getContent());
        out.put("page", result.getNumber());
        out.put("size", result.getSize());
        out.put("totalElements", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        return out;
    }

    public byte[] exportLogs(String level, String category, String q, LocalDate from, LocalDate to) {
        List<SystemLog> logs = logRepository.findAll(spec(level, category, q, from, to),
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        StringBuilder csv = new StringBuilder("﻿Date;Niveau;Catégorie;Action;Message;Utilisateur;Rôle;IP;Méthode;Chemin;Statut;Durée (ms)\n");
        for (SystemLog l : logs) {
            csv.append(String.join(";", List.of(
                    l.getCreatedAt().format(fmt), l.getLevel(), l.getCategory(), cell(l.getAction()), cell(l.getMessage()),
                    cell(l.getActorEmail()), cell(l.getActorRole()), cell(l.getIpAddress()), cell(l.getHttpMethod()),
                    cell(l.getPath()), l.getStatus() != null ? l.getStatus().toString() : "",
                    l.getDurationMs() != null ? l.getDurationMs().toString() : ""))).append("\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Specification<SystemLog> spec(String level, String category, String q, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (level != null && !level.isBlank()) p.add(cb.equal(root.get("level"), level));
            if (category != null && !category.isBlank()) p.add(cb.equal(root.get("category"), category));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("message")), like),
                        cb.like(cb.lower(root.get("actorEmail")), like),
                        cb.like(cb.lower(root.get("path")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    // ── Outils ──────────────────────────────────────────────────

    private static List<Long> perDay(List<String> days, java.util.stream.Stream<LocalDateTime> dates) {
        Map<String, Long> counts = dates.filter(Objects::nonNull)
                .collect(Collectors.groupingBy(d -> d.toLocalDate().toString(), Collectors.counting()));
        return days.stream().map(d -> counts.getOrDefault(d, 0L)).toList();
    }

    private static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> getter) {
        return list.stream().map(getter).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double avg(List<Double> values) {
        OptionalDouble avg = values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : null;
    }

    private static String cell(String v) {
        if (v == null) return "";
        String safe = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return safe.contains(";") || safe.contains("\"") || safe.contains("\n") ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
