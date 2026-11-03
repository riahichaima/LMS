package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.LessonProgressRepository;
import com.course.lms.repository.ResourceProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Progression réelle d'un cours :
 * - ressource : pourcentage mesuré (pages PDF vues, part de vidéo/audio, lien ouvert = 100 %) ;
 * - leçon : 100 % si marquée terminée, sinon moyenne de ses ressources ; terminée automatiquement à 90 % partout ;
 * - cours : moyenne de ses leçons (enregistrée dans l'inscription, utilisée par tous les tableaux de bord).
 */
@Service
@Transactional
public class CourseProgressService {

    /** Au-delà, une ressource est considérée comme vue en entier (fin de vidéo, dernière page). */
    static final int SEEN = 90;

    private final ResourceProgressRepository resourceProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseProgressService(ResourceProgressRepository resourceProgressRepository,
                                 LessonProgressRepository lessonProgressRepository, EnrollmentRepository enrollmentRepository) {
        this.resourceProgressRepository = resourceProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** Enregistre l'avancement d'une ressource (jamais à la baisse) puis recalcule le cours. */
    public Snapshot recordResource(User student, LessonResource resource, Enrollment enrollment, int percent) {
        Lesson lesson = resource.getLesson();
        Course course = lesson.getChapter().getCourse();
        int value = Math.max(0, Math.min(100, percent));
        ResourceProgress p = resourceProgressRepository.findByStudentIdAndResourceId(student.getId(), resource.getId())
                .orElseGet(() -> new ResourceProgress(student, resource, lesson.getId(), course.getId()));
        if (p.getId() == null || value > p.getPercent()) {
            p.setPercent(value);
            p.setUpdatedAt(LocalDateTime.now());
            resourceProgressRepository.save(p);
        }

        // Toutes les ressources de la leçon vues : la leçon est terminée sans clic supplémentaire
        Map<Long, Integer> byResource = progressByResource(student.getId(), course.getId());
        boolean allSeen = !lesson.getResources().isEmpty()
                && lesson.getResources().stream().allMatch(r -> byResource.getOrDefault(r.getId(), 0) >= SEEN);
        if (allSeen && !lessonProgressRepository.existsByStudentIdAndLessonId(student.getId(), lesson.getId())) {
            lessonProgressRepository.save(new LessonProgress(student, lesson, course.getId()));
        }
        return refresh(student, course, enrollment);
    }

    /** Recalcule et enregistre la progression du cours dans l'inscription. */
    public Snapshot refresh(User student, Course course, Enrollment enrollment) {
        Snapshot s = compute(student.getId(), course);
        enrollment.setProgressPercentage(s.coursePercent());
        enrollment.setLastAccessedAt(LocalDateTime.now());
        if (s.coursePercent() >= 100 && !Boolean.TRUE.equals(enrollment.getIsCompleted())) {
            enrollment.setIsCompleted(true);
            enrollment.setCompletedAt(LocalDateTime.now());
        }
        enrollmentRepository.save(enrollment);
        return s;
    }

    @Transactional(readOnly = true)
    public Snapshot compute(Long studentId, Course course) {
        Map<Long, Integer> byResource = progressByResource(studentId, course.getId());
        Set<Long> completed = new HashSet<>(lessonProgressRepository.findCompletedLessonIds(studentId, course.getId()));
        Map<Long, Integer> byLesson = new LinkedHashMap<>();
        for (Chapter chapter : course.getChapters()) {
            for (Lesson lesson : chapter.getLessons()) {
                int value;
                if (completed.contains(lesson.getId())) value = 100;
                else if (lesson.getResources().isEmpty()) value = 0;
                else value = (int) Math.round(lesson.getResources().stream()
                        .mapToInt(r -> byResource.getOrDefault(r.getId(), 0)).average().orElse(0));
                byLesson.put(lesson.getId(), value);
            }
        }
        int course100 = byLesson.isEmpty() ? 0
                : (int) Math.round(byLesson.values().stream().mapToInt(Integer::intValue).average().orElse(0));
        return new Snapshot(Math.min(100, course100), byLesson, byResource, completed);
    }

    private Map<Long, Integer> progressByResource(Long studentId, Long courseId) {
        return resourceProgressRepository.findByStudentIdAndCourseId(studentId, courseId).stream()
                .collect(Collectors.toMap(p -> p.getResource().getId(), ResourceProgress::getPercent, Math::max));
    }

    /** Progression du cours, de chaque leçon et de chaque ressource (en %). */
    public record Snapshot(int coursePercent, Map<Long, Integer> lessons, Map<Long, Integer> resources, Set<Long> completedLessons) {}
}
