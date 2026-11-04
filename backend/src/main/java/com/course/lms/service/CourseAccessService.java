package com.course.lms.service;

import com.course.lms.dto.ChapterResponse;
import com.course.lms.dto.CourseResponse;
import com.course.lms.dto.LessonResponse;
import com.course.lms.model.Course;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Accès au contenu des cours payants : réservé aux étudiants inscrits (après paiement confirmé),
 * à l'enseignant du cours et aux administrateurs. Sans accès, le programme reste visible
 * (chapitres, leçons) mais les ressources (PDF, vidéos, liens) sont masquées.
 */
@Service
public class CourseAccessService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.course.lms.repository.ChapterRepository chapterRepository;

    @Autowired
    private com.course.lms.repository.LessonRepository lessonRepository;

    @Autowired
    private com.course.lms.repository.LessonResourceRepository resourceRepository;

    @Autowired
    private com.course.lms.repository.QuizRepository quizRepository;

    public void requireChapterAccess(Long chapterId) {
        chapterRepository.findById(chapterId).ifPresent(ch -> requireAccess(ch.getCourse()));
    }

    public void requireLessonAccess(Long lessonId) {
        lessonRepository.findById(lessonId).ifPresent(l -> requireAccess(l.getChapter().getCourse()));
    }

    public void requireResourceAccess(Long resourceId) {
        resourceRepository.findById(resourceId).ifPresent(r -> requireAccess(r.getLesson().getChapter().getCourse()));
    }

    public void requireQuizAccess(Long quizId) {
        quizRepository.findById(quizId).ifPresent(q -> {
            if (q.getChapter() != null) {
                requireAccess(q.getChapter().getCourse());
            } else if (q.getLesson() != null) {
                requireAccess(q.getLesson().getChapter().getCourse());
            }
        });
    }

    public List<ChapterResponse> protectChaptersOfCourse(Long courseId, List<ChapterResponse> chapters) {
        return courseRepository.findById(courseId).map(c -> protectChapters(c, chapters)).orElse(chapters);
    }

    public ChapterResponse protectChapter(Long chapterId, ChapterResponse chapter) {
        chapterRepository.findById(chapterId).ifPresent(ch -> protectChapters(ch.getCourse(), java.util.List.of(chapter)));
        return chapter;
    }

    public List<LessonResponse> protectLessonsOfChapter(Long chapterId, List<LessonResponse> lessons) {
        return chapterRepository.findById(chapterId).map(ch -> protectLessons(ch.getCourse(), lessons)).orElse(lessons);
    }

    public LessonResponse protectLesson(Long lessonId, LessonResponse lesson) {
        lessonRepository.findById(lessonId).ifPresent(l -> protectLessons(l.getChapter().getCourse(), java.util.List.of(lesson)));
        return lesson;
    }

    public boolean canAccess(Course course) {
        return canAccess(course.getId(), course.getPricingType(), course.getTeacher() != null ? course.getTeacher().getId() : null);
    }

    public boolean canAccess(Long courseId) {
        return courseRepository.findById(courseId).map(this::canAccess).orElse(false);
    }

    public boolean canAccess(Long courseId, String pricingType, Long teacherId) {
        if (!"PAID".equals(pricingType)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return false;
        }
        return user.getId().equals(teacherId)
                || enrollmentRepository.findByStudentIdAndCourseId(user.getId(), courseId).isPresent();
    }

    public void requireAccess(Course course) {
        if (!canAccess(course)) {
            throw new SecurityException("Ce contenu est réservé aux étudiants ayant acheté le cours.");
        }
    }

    // Masque les ressources d'un cours payant non acheté (le programme reste visible)
    public CourseResponse protect(CourseResponse course) {
        if (course != null && !canAccess(course.getId(), course.getPricingType(), course.getTeacherId())) {
            course.setChapters(redactChapters(course.getChapters()));
        }
        return course;
    }

    public List<ChapterResponse> protectChapters(Course course, List<ChapterResponse> chapters) {
        return canAccess(course) ? chapters : redactChapters(chapters);
    }

    public List<LessonResponse> protectLessons(Course course, List<LessonResponse> lessons) {
        if (canAccess(course) || lessons == null) {
            return lessons;
        }
        lessons.forEach(l -> l.setResources(new ArrayList<>()));
        return lessons;
    }

    private List<ChapterResponse> redactChapters(List<ChapterResponse> chapters) {
        if (chapters != null) {
            chapters.forEach(ch -> {
                if (ch.getLessons() != null) {
                    ch.getLessons().forEach(l -> l.setResources(new ArrayList<>()));
                }
            });
        }
        return chapters;
    }
}
