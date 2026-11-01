package com.course.lms.service;

import com.course.lms.dto.CourseRequest;
import com.course.lms.dto.CourseResponse;
import com.course.lms.dto.ChapterRequest;
import com.course.lms.dto.LessonRequest;
import com.course.lms.dto.LessonResourceRequest;
import com.course.lms.model.Course;
import com.course.lms.model.Chapter;
import com.course.lms.model.Lesson;
import com.course.lms.model.LessonResource;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.repository.ChapterRepository;
import com.course.lms.repository.LessonRepository;
import com.course.lms.repository.LessonResourceRepository;
import com.course.lms.repository.AssignmentRepository;
import com.course.lms.repository.QuizRepository;
import com.course.lms.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private LessonResourceRepository lessonResourceRepository;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private com.course.lms.repository.PaymentRepository paymentRepository;

    public CourseResponse createCourse(CourseRequest request, String userEmail) {
        User teacher;
        if (request.getTeacherId() != null) {
            teacher = userRepository.findById(request.getTeacherId())
                    .orElseThrow(() -> new RuntimeException("Professeur spécifié non trouvé"));
        } else {
            teacher = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Utilisateur connecté non trouvé"));
        }

        Course course = new Course(request.getTitle(), request.getDescription(), teacher);
        course.setThumbnail(request.getThumbnail());
        applyPricing(course, request);
        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        } else {
            course.setStatus("DRAFT");
        }
        Course savedCourse = courseRepository.save(course);
        notifyStudentsOfNewCourse(savedCourse);

        return mapToResponse(savedCourse);
    }

    @Transactional
    public CourseResponse updateFullCourse(Long id, CourseRequest request, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (!isAdmin && !course.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à modifier ce cours");
        }

        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setThumbnail(request.getThumbnail());
        applyPricing(course, request);
        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        }

        // Mise à jour en place : les chapitres / leçons envoyés avec un id sont conservés
        // (les quiz et TP générés par l'IA restent liés), les autres sont créés ou supprimés.
        List<ChapterRequest> chapterRequests = request.getChapters() != null ? request.getChapters() : new ArrayList<>();
        Set<Long> keptChapterIds = chapterRequests.stream().map(ChapterRequest::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (Chapter removed : new ArrayList<>(course.getChapters())) {
            if (!keptChapterIds.contains(removed.getId())) {
                deleteChapterAssessments(removed);
                course.getChapters().remove(removed);
            }
        }

        for (ChapterRequest chapReq : chapterRequests) {
            Chapter chapter = course.getChapters().stream()
                    .filter(c -> chapReq.getId() != null && chapReq.getId().equals(c.getId()))
                    .findFirst().orElse(null);
            if (chapter == null) {
                chapter = new Chapter();
                chapter.setCourse(course);
                course.getChapters().add(chapter);
            }
            chapter.setTitle(chapReq.getTitle());
            chapter.setDescription(chapReq.getDescription());
            chapter.setOrderIndex(chapReq.getOrderIndex());
            Chapter savedChapter = chapterRepository.save(chapter);

            List<LessonRequest> lessonRequests = chapReq.getLessons() != null ? chapReq.getLessons() : new ArrayList<>();
            Set<Long> keptLessonIds = lessonRequests.stream().map(LessonRequest::getId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            for (Lesson removed : new ArrayList<>(savedChapter.getLessons())) {
                if (!keptLessonIds.contains(removed.getId())) {
                    deleteLessonAssessments(removed.getId());
                    savedChapter.getLessons().remove(removed);
                }
            }

            for (LessonRequest lesReq : lessonRequests) {
                Lesson lesson = savedChapter.getLessons().stream()
                        .filter(l -> lesReq.getId() != null && lesReq.getId().equals(l.getId()))
                        .findFirst().orElse(null);
                if (lesson == null) {
                    lesson = new Lesson();
                    lesson.setChapter(savedChapter);
                    savedChapter.getLessons().add(lesson);
                }
                lesson.setTitle(lesReq.getTitle());
                lesson.setDescription(lesReq.getDescription());
                lesson.setOrderIndex(lesReq.getOrderIndex());

                // Les ressources sont remplacées par la liste envoyée (les fichiers déjà importés
                // reviennent avec leur filePath, les nouveaux viennent d'être enregistrés)
                lesson.getResources().clear();
                Lesson savedLesson = lessonRepository.saveAndFlush(lesson);
                if (lesReq.getResources() != null) {
                    for (LessonResourceRequest resReq : lesReq.getResources()) {
                        LessonResource resource = new LessonResource();
                        resource.setLesson(savedLesson);
                        resource.setResourceType(resReq.getResourceType());
                        resource.setTitle(resReq.getTitle());
                        resource.setFilePath(resReq.getFilePath());
                        resource.setExternalUrl(resReq.getExternalUrl());
                        resource.setMimeType(resReq.getMimeType());
                        resource.setSize(resReq.getSize());
                        savedLesson.getResources().add(resource);
                    }
                }
                lessonRepository.save(savedLesson);
            }
        }

        return mapToResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse createFullCourse(CourseRequest request, String userEmail) {
        User teacher;
        if (request.getTeacherId() != null) {
            teacher = userRepository.findById(request.getTeacherId())
                    .orElseThrow(() -> new RuntimeException("Professeur spécifié non trouvé"));
        } else {
            teacher = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Utilisateur connecté non trouvé"));
        }

        // 1. Create and save the course
        Course course = new Course(request.getTitle(), request.getDescription(), teacher);
        course.setThumbnail(request.getThumbnail());
        applyPricing(course, request);
        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        } else {
            course.setStatus("DRAFT");
        }
        Course savedCourse = courseRepository.save(course);
        notifyStudentsOfNewCourse(savedCourse);

        // 2. Create chapters
        if (request.getChapters() != null) {
            for (ChapterRequest chapReq : request.getChapters()) {
                Chapter chapter = new Chapter();
                chapter.setTitle(chapReq.getTitle());
                chapter.setDescription(chapReq.getDescription());
                chapter.setOrderIndex(chapReq.getOrderIndex());
                chapter.setCourse(savedCourse);
                Chapter savedChapter = chapterRepository.save(chapter);

                // 3. Create lessons
                if (chapReq.getLessons() != null) {
                    for (LessonRequest lesReq : chapReq.getLessons()) {
                        Lesson lesson = new Lesson();
                        lesson.setTitle(lesReq.getTitle());
                        lesson.setDescription(lesReq.getDescription());
                        lesson.setOrderIndex(lesReq.getOrderIndex());
                        lesson.setChapter(savedChapter);
                        Lesson savedLesson = lessonRepository.save(lesson);

                        // 4. Create resources
                        if (lesReq.getResources() != null) {
                            for (LessonResourceRequest resReq : lesReq.getResources()) {
                                LessonResource resource = new LessonResource();
                                resource.setLesson(savedLesson);
                                resource.setResourceType(resReq.getResourceType());
                                resource.setTitle(resReq.getTitle());
                                resource.setFilePath(resReq.getFilePath());
                                resource.setExternalUrl(resReq.getExternalUrl());
                                resource.setMimeType(resReq.getMimeType());
                                resource.setSize(resReq.getSize());
                                lessonResourceRepository.save(resource);
                                
                                savedLesson.getResources().add(resource);
                            }
                        }
                        savedChapter.getLessons().add(savedLesson);
                    }
                }
                savedCourse.getChapters().add(savedChapter);
            }
        }

        return mapToResponse(savedCourse);
    }

    public Page<CourseResponse> getAllCourses(Pageable pageable) {
        return courseRepository.findAll(pageable).map(this::mapToResponse);
    }

    public Page<CourseResponse> searchCourses(String title, Pageable pageable) {
        return courseRepository.findByTitleContainingIgnoreCase(title, pageable)
                .map(this::mapToResponse);
    }

    public List<CourseResponse> getMyCourses(String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new RuntimeException("Professeur non trouvé"));

        return courseRepository.findByTeacherId(teacher.getId())
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));
        return mapToResponse(course);
    }

    public CourseResponse updateCourse(Long id, CourseRequest request, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        // Vérification des droits
        if (!isAdmin && !course.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à modifier ce cours");
        }

        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setThumbnail(request.getThumbnail());
        applyPricing(course, request);
        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        }
        Course updatedCourse = courseRepository.save(course);

        return mapToResponse(updatedCourse);
    }

    @Transactional
    public void deleteCourse(Long id, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (!isAdmin && !course.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à supprimer ce cours");
        }

        // Un cours déjà vendu garde son historique financier : il ne peut pas être supprimé
        if (paymentRepository.existsByCourseIdAndStatus(course.getId(), com.course.lms.model.Payment.PAID)) {
            throw new IllegalStateException("Ce cours a déjà été acheté : il ne peut pas être supprimé (passez-le en brouillon).");
        }
        paymentRepository.deleteAll(paymentRepository.findByCourseIdAndStatusNot(course.getId(), com.course.lms.model.Payment.PAID));

        // Les quiz / TP générés référencent les chapitres et leçons : on les supprime d'abord
        for (Chapter chapter : course.getChapters()) {
            deleteChapterAssessments(chapter);
        }
        courseRepository.delete(course);
    }

    // Quiz et TP (et leurs soumissions) rattachés à un chapitre ou à l'une de ses leçons
    private void deleteChapterAssessments(Chapter chapter) {
        for (Lesson lesson : chapter.getLessons()) {
            deleteLessonAssessments(lesson.getId());
        }
        quizRepository.findByChapterId(chapter.getId()).forEach(this::deleteQuiz);
        assignmentRepository.deleteAll(assignmentRepository.findByChapterId(chapter.getId()));
    }

    private void deleteLessonAssessments(Long lessonId) {
        quizRepository.findByLessonId(lessonId).forEach(this::deleteQuiz);
        assignmentRepository.deleteAll(assignmentRepository.findByLessonId(lessonId));
    }

    private void deleteQuiz(com.course.lms.model.Quiz quiz) {
        submissionRepository.deleteAll(submissionRepository.findByQuizId(quiz.getId()));
        quizRepository.delete(quiz);
    }

    public CourseResponse assignTeacherToCourse(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Formateur non trouvé"));

        if (teacher.getRole() == null || !"TEACHER".equals(teacher.getRole().getName())) {
            throw new RuntimeException("L'utilisateur sélectionné n'a pas le rôle TEACHER");
        }

        course.setTeacher(teacher);
        Course updated = courseRepository.save(course);
        return mapToResponse(updated);
    }

    private CourseResponse mapToResponse(Course course) {
        CourseResponse response = new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getTeacher().getId(),
                course.getTeacher().getName(),
                course.getStatus(),
                course.getThumbnail(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getChapters().stream().map(chapterService::mapToResponse).collect(Collectors.toList())
        );
        response.setPricingType(course.getPricingType());
        response.setPrice(course.isPaid() ? course.getPrice() : null);
        return response;
    }

    // Prix minimum accepté par Stripe pour une carte en USD : 0,50 $
    private static final java.math.BigDecimal MIN_PRICE = new java.math.BigDecimal("0.50");
    private static final java.math.BigDecimal MAX_PRICE = new java.math.BigDecimal("10000.00");

    // FREE (par défaut) ou PAID avec un prix en USD validé côté serveur
    private void applyPricing(Course course, CourseRequest request) {
        if (request.getPricingType() == null) {
            // Modification sans tarification envoyée : on garde celle du cours (nouveau cours : gratuit)
            if (course.getId() == null) {
                course.setPricingType("FREE");
                course.setPrice(null);
            }
            return;
        }
        String type = request.getPricingType().trim().toUpperCase();
        if ("PAID".equals(type)) {
            java.math.BigDecimal price = request.getPrice();
            if (price == null || price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
                throw new IllegalArgumentException("Le prix d'un cours payant doit être compris entre 0,50 $ et 10 000 $.");
            }
            if (price.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Le prix doit avoir au plus deux décimales.");
            }
            course.setPricingType("PAID");
            course.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
        } else if ("FREE".equals(type)) {
            course.setPricingType("FREE");
            course.setPrice(null);
        } else {
            throw new IllegalArgumentException("Type de tarification invalide : FREE ou PAID attendu.");
        }
    }

    /** Nouveau cours au catalogue : notification temps réel à tous les étudiants. */
    private void notifyStudentsOfNewCourse(Course course) {
        String teacher = course.getTeacher() != null ? course.getTeacher().getName() : "un formateur";
        notificationService.notifyRole("STUDENT", "📚 Nouveau cours : « " + course.getTitle() + " »",
                teacher + " vient de publier un nouveau cours" + (course.isPaid() ? " (" + course.getPrice() + " $)" : " gratuit") + ".",
                "NEW_COURSE", course.getId());
    }
}
