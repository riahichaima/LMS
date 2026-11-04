package com.course.lms.service;

import com.course.lms.dto.FeedbackDtos.*;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Avis des étudiants :
 *  - note d'un cours (1 à 5 étoiles + avis), réservée aux étudiants inscrits ;
 *  - évaluation d'un formateur en pourcentage (0 à 100 %), réservée à ses étudiants ;
 *  - « J'aime » / « Je n'aime pas » et commentaires sur un cours entier et sous chacune de ses ressources ;
 *  - profil public du formateur (étudiants, ancienneté, évaluation et notes calculées à partir des avis réels).
 * Chaque avis d'un étudiant notifie le formateur en temps réel ; une réponse du formateur notifie les étudiants.
 */
@Service
@Transactional
public class FeedbackService {

    private static final int MAX_TEXT = 1000;

    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LessonResourceRepository resourceRepository;
    @Autowired private CourseRatingRepository ratingRepository;
    @Autowired private TeacherRatingRepository teacherRatingRepository;
    @Autowired private ResourceCommentRepository commentRepository;
    @Autowired private ResourceReactionRepository reactionRepository;
    @Autowired private CourseCommentRepository courseCommentRepository;
    @Autowired private CourseReactionRepository courseReactionRepository;
    @Autowired private NotificationService notificationService;

    // ── Notes des cours (étoiles) ───────────────────────────────

    @Transactional(readOnly = true)
    public RatingSummary ratingSummary(Long courseId, String email) {
        return summary(findCourse(courseId), findUser(email));
    }

    public RatingSummary rate(Long courseId, String email, RatingRequest request) {
        User student = findUser(email);
        Course course = findCourse(courseId);
        requireEnrolledStudent(student, course, "Seuls les étudiants inscrits à ce cours peuvent le noter.");
        if (request == null || request.stars() == null || request.stars() < 1 || request.stars() > 5) {
            throw new IllegalArgumentException("La note doit être comprise entre 1 et 5 étoiles.");
        }
        String comment = clean(request.comment());

        Optional<CourseRating> existing = ratingRepository.findByCourseIdAndStudentId(courseId, student.getId());
        CourseRating rating = existing.orElseGet(CourseRating::new);
        boolean changed = existing.isEmpty() || rating.getStars() != request.stars()
                || !Objects.equals(rating.getComment(), comment);
        rating.setCourse(course);
        rating.setStudent(student);
        rating.setStars(request.stars());
        rating.setComment(comment);
        if (existing.isPresent()) rating.setUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        if (changed) {
            notificationService.notifyStaff("⭐ Avis sur un cours : " + request.stars() + "/5",
                    student.getName() + " a noté « " + course.getTitle() + " »" + (comment != null ? " : « " + comment + " »" : "."),
                    "ADMIN_FEEDBACK", course.getId());
        }
        if (changed && course.getTeacher() != null) {
            notificationService.sendNotification(course.getTeacher(),
                    (existing.isEmpty() ? "Nouvelle note : " : "Note modifiée : ") + stars(request.stars()) + " (" + request.stars() + "/5)",
                    student.getName() + " a noté votre cours « " + course.getTitle() + " »"
                            + (comment != null ? " : « " + comment + " »" : "."),
                    "RATING", course.getId());
        }
        return summary(course, student);
    }

    public RatingSummary deleteRating(Long courseId, String email) {
        User student = findUser(email);
        Course course = findCourse(courseId);
        ratingRepository.findByCourseIdAndStudentId(courseId, student.getId()).ifPresent(ratingRepository::delete);
        ratingRepository.flush();
        return summary(course, student);
    }

    @Transactional(readOnly = true)
    public List<Review> reviews(Long courseId) {
        Course course = findCourse(courseId);
        return ratingRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(r -> review(r, course)).toList();
    }

    /** Moyenne et nombre de notes de chaque cours (catalogue). */
    @Transactional(readOnly = true)
    public List<CourseRatingLine> catalogRatings() {
        return ratingRepository.summarizeAll().stream()
                .map(row -> new CourseRatingLine((Long) row[0], round1(((Number) row[1]).doubleValue()), ((Number) row[2]).longValue()))
                .toList();
    }

    // ── Évaluation des formateurs (%) ───────────────────────────

    @Transactional(readOnly = true)
    public TeacherRatingSummary teacherRatingSummary(Long teacherId, String email) {
        return teacherSummary(findTeacher(teacherId), findUser(email));
    }

    public TeacherRatingSummary rateTeacher(Long teacherId, String email, TeacherRatingRequest request) {
        User student = findUser(email);
        User teacher = findTeacher(teacherId);
        if (!canRateTeacher(student, teacher)) {
            throw new SecurityException("Seuls les étudiants inscrits à un cours de ce formateur peuvent l'évaluer.");
        }
        if (request == null || request.percent() == null || request.percent() < 0 || request.percent() > 100) {
            throw new IllegalArgumentException("L'évaluation doit être comprise entre 0 et 100 %.");
        }
        String comment = clean(request.comment());

        Optional<TeacherRating> existing = teacherRatingRepository.findByTeacherIdAndStudentId(teacherId, student.getId());
        TeacherRating rating = existing.orElseGet(TeacherRating::new);
        boolean changed = existing.isEmpty() || rating.getPercent() != request.percent()
                || !Objects.equals(rating.getComment(), comment);
        rating.setTeacher(teacher);
        rating.setStudent(student);
        rating.setPercent(request.percent());
        rating.setComment(comment);
        if (existing.isPresent()) rating.setUpdatedAt(LocalDateTime.now());
        teacherRatingRepository.save(rating);

        if (changed) {
            notificationService.notifyStaff("🎯 Évaluation d'un enseignant : " + request.percent() + " %",
                    student.getName() + " a évalué " + teacher.getName() + (comment != null ? " : « " + comment + " »" : "."),
                    "ADMIN_FEEDBACK", teacher.getId());
            notificationService.sendNotification(teacher,
                    (existing.isEmpty() ? "🎯 Nouvelle évaluation : " : "🎯 Évaluation modifiée : ") + request.percent() + " %",
                    student.getName() + " vous a évalué à " + request.percent() + " %"
                            + (comment != null ? " : « " + comment + " »" : "."),
                    "TEACHER_RATING", teacher.getId());
        }
        return teacherSummary(teacher, student);
    }

    public TeacherRatingSummary deleteTeacherRating(Long teacherId, String email) {
        User student = findUser(email);
        User teacher = findTeacher(teacherId);
        teacherRatingRepository.findByTeacherIdAndStudentId(teacherId, student.getId()).ifPresent(teacherRatingRepository::delete);
        teacherRatingRepository.flush();
        return teacherSummary(teacher, student);
    }

    // ── Réactions et commentaires sur un cours entier ───────────

    @Transactional(readOnly = true)
    public ReactionSummary courseFeedback(Long courseId, String email) {
        User user = findUser(email);
        Course course = findCourse(courseId);
        requireAccess(user, course);
        return courseReactionSummary(course, user);
    }

    public ReactionSummary reactToCourse(Long courseId, String email, ReactionRequest request) {
        User student = findUser(email);
        Course course = findCourse(courseId);
        requireEnrolledStudent(student, course, "Seuls les étudiants inscrits à ce cours peuvent y réagir.");
        String type = reactionType(request);

        Optional<CourseReaction> existing = courseReactionRepository.findByCourseIdAndUserId(courseId, student.getId());
        boolean notify;
        if (existing.isPresent() && existing.get().getType().equals(type)) {
            courseReactionRepository.delete(existing.get());
            notify = false;
        } else {
            CourseReaction reaction = existing.orElseGet(CourseReaction::new);
            reaction.setCourse(course);
            reaction.setUser(student);
            reaction.setType(type);
            reaction.setCreatedAt(LocalDateTime.now());
            courseReactionRepository.save(reaction);
            notify = true;
        }
        courseReactionRepository.flush();

        if (notify && course.getTeacher() != null) {
            boolean like = ResourceReaction.LIKE.equals(type);
            notificationService.sendNotification(course.getTeacher(),
                    like ? "👍 Cours apprécié" : "👎 Cours pas apprécié",
                    student.getName() + (like ? " aime " : " n'aime pas ") + "votre cours « " + course.getTitle() + " ».",
                    "REACTION", course.getId());
            if (!like) notificationService.notifyStaff("👎 Cours pas apprécié",
                    student.getName() + " n'aime pas le cours « " + course.getTitle() + " ».", "ADMIN_FEEDBACK", course.getId());
        }
        return courseReactionSummary(course, student);
    }

    @Transactional(readOnly = true)
    public List<CommentView> courseDiscussion(Long courseId, String email) {
        User user = findUser(email);
        Course course = findCourse(courseId);
        requireAccess(user, course);
        return courseCommentRepository.findByCourseIdOrderByCreatedAtAscIdAsc(courseId).stream()
                .map(c -> commentView(c.getId(), c.getAuthor(), c.getContent(), c.getCreatedAt(), user, course)).toList();
    }

    public CommentView addCourseComment(Long courseId, String email, CommentRequest request) {
        User author = findUser(email);
        Course course = findCourse(courseId);
        boolean teacher = requireCanComment(author, course);
        String content = requireText(request);

        CourseComment comment = new CourseComment();
        comment.setCourse(course);
        comment.setAuthor(author);
        comment.setContent(content);
        courseCommentRepository.save(comment);

        String where = "le cours « " + course.getTitle() + " »";
        if (!teacher) {
            notifyTeacherOfComment(course, author, where, content);
        } else {
            notifyCommentersOfReply(courseCommentRepository.findByCourseIdOrderByCreatedAtAscIdAsc(courseId).stream()
                    .map(CourseComment::getAuthor).toList(), author, course, where, content);
        }
        return commentView(comment.getId(), author, content, comment.getCreatedAt(), author, course);
    }

    public void deleteCourseComment(Long commentId, String email) {
        User user = findUser(email);
        CourseComment comment = courseCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Commentaire introuvable."));
        requireCanDelete(user, comment.getAuthor(), comment.getCourse());
        courseCommentRepository.delete(comment);
    }

    // ── Réactions et commentaires sur une ressource ─────────────

    @Transactional(readOnly = true)
    public ReactionSummary resourceFeedback(Long resourceId, String email) {
        User user = findUser(email);
        LessonResource resource = findResource(resourceId);
        requireAccess(user, courseOf(resource));
        return resourceReactionSummary(resource, user);
    }

    /** Même réaction une 2e fois = retrait ; réaction opposée = changement d'avis. */
    public ReactionSummary react(Long resourceId, String email, ReactionRequest request) {
        User student = findUser(email);
        LessonResource resource = findResource(resourceId);
        Course course = courseOf(resource);
        requireEnrolledStudent(student, course, "Seuls les étudiants inscrits à ce cours peuvent réagir à ses contenus.");
        String type = reactionType(request);

        Optional<ResourceReaction> existing = reactionRepository.findByResourceIdAndUserId(resourceId, student.getId());
        boolean notify;
        if (existing.isPresent() && existing.get().getType().equals(type)) {
            reactionRepository.delete(existing.get());
            notify = false;
        } else {
            ResourceReaction reaction = existing.orElseGet(ResourceReaction::new);
            reaction.setResource(resource);
            reaction.setUser(student);
            reaction.setType(type);
            reaction.setCreatedAt(LocalDateTime.now());
            reactionRepository.save(reaction);
            notify = true;
        }
        reactionRepository.flush();

        if (notify && course.getTeacher() != null) {
            boolean like = ResourceReaction.LIKE.equals(type);
            notificationService.sendNotification(course.getTeacher(),
                    like ? "👍 Contenu apprécié" : "👎 Contenu pas apprécié",
                    student.getName() + (like ? " aime " : " n'aime pas ") + "« " + resourceTitle(resource) + " » (" + course.getTitle() + ").",
                    "REACTION", course.getId());
            if (!like) notificationService.notifyStaff("👎 Contenu pas apprécié",
                    student.getName() + " n'aime pas « " + resourceTitle(resource) + " » (" + course.getTitle() + ").", "ADMIN_FEEDBACK", course.getId());
        }
        return resourceReactionSummary(resource, student);
    }

    @Transactional(readOnly = true)
    public List<CommentView> comments(Long resourceId, String email) {
        User user = findUser(email);
        LessonResource resource = findResource(resourceId);
        Course course = courseOf(resource);
        requireAccess(user, course);
        return commentRepository.findByResourceIdOrderByCreatedAtAscIdAsc(resourceId).stream()
                .map(c -> commentView(c.getId(), c.getAuthor(), c.getContent(), c.getCreatedAt(), user, course)).toList();
    }

    public CommentView addComment(Long resourceId, String email, CommentRequest request) {
        User author = findUser(email);
        LessonResource resource = findResource(resourceId);
        Course course = courseOf(resource);
        boolean teacher = requireCanComment(author, course);
        String content = requireText(request);

        ResourceComment comment = new ResourceComment();
        comment.setResource(resource);
        comment.setAuthor(author);
        comment.setContent(content);
        commentRepository.save(comment);

        String where = "« " + resourceTitle(resource) + " » (" + course.getTitle() + ")";
        if (!teacher) {
            notifyTeacherOfComment(course, author, where, content);
        } else {
            notifyCommentersOfReply(commentRepository.findByResourceIdOrderByCreatedAtAscIdAsc(resourceId).stream()
                    .map(ResourceComment::getAuthor).toList(), author, course, where, content);
        }
        return commentView(comment.getId(), author, content, comment.getCreatedAt(), author, course);
    }

    public void deleteComment(Long commentId, String email) {
        User user = findUser(email);
        ResourceComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Commentaire introuvable."));
        requireCanDelete(user, comment.getAuthor(), courseOf(comment.getResource()));
        commentRepository.delete(comment);
    }

    /** Tous les commentaires d'un cours, sur le cours entier et sous ses contenus (formateur du cours ou administrateur). */
    @Transactional(readOnly = true)
    public List<CourseCommentView> courseComments(Long courseId, String email) {
        User user = findUser(email);
        Course course = findCourse(courseId);
        if (!isCourseTeacher(user, course) && !isAdmin(user)) {
            throw new SecurityException("Réservé au formateur du cours.");
        }
        Long teacherId = course.getTeacher() != null ? course.getTeacher().getId() : null;
        List<CourseCommentView> all = new ArrayList<>();
        courseCommentRepository.findByCourseIdOrderByCreatedAtAscIdAsc(courseId).forEach(c -> all.add(new CourseCommentView(
                c.getId(), "COURSE", null, "Cours (général)", null, null, c.getAuthor().getId(), c.getAuthor().getName(),
                c.getAuthor().getId().equals(teacherId), c.getContent(), c.getCreatedAt())));
        commentRepository.findByCourseId(courseId).forEach(c -> all.add(new CourseCommentView(
                c.getId(), "RESOURCE", c.getResource().getId(), resourceTitle(c.getResource()), c.getResource().getResourceType().toString(),
                c.getResource().getLesson().getTitle(), c.getAuthor().getId(), c.getAuthor().getName(),
                c.getAuthor().getId().equals(teacherId), c.getContent(), c.getCreatedAt())));
        all.sort(Comparator.comparing(CourseCommentView::createdAt).reversed());
        return all;
    }

    // ── Profil formateur ────────────────────────────────────────

    @Transactional(readOnly = true)
    public TeacherProfile teacherProfile(Long teacherId) {
        User teacher = findTeacher(teacherId);

        List<Course> courses = courseRepository.findByTeacherId(teacherId).stream()
                .sorted(Comparator.comparing(Course::getTitle, String.CASE_INSENSITIVE_ORDER)).toList();
        List<CourseRating> ratings = ratingRepository.findByTeacherId(teacherId);
        Map<Long, List<CourseRating>> ratingsByCourse = ratings.stream().collect(Collectors.groupingBy(r -> r.getCourse().getId()));
        List<TeacherRating> evaluations = teacherRatingRepository.findByTeacherIdOrderByCreatedAtDescIdDesc(teacherId);

        Set<Long> students = new HashSet<>();
        long enrollments = 0;
        List<ProfileCourse> courseList = new ArrayList<>();
        for (Course c : courses) {
            List<Enrollment> list = enrollmentRepository.findByCourseId(c.getId());
            enrollments += list.size();
            list.forEach(e -> students.add(e.getStudent().getId()));
            List<CourseRating> cr = ratingsByCourse.getOrDefault(c.getId(), List.of());
            courseList.add(new ProfileCourse(c.getId(), c.getTitle(), c.getPricingType(), list.size(), average(cr), cr.size()));
        }

        // Appréciation : réactions sur les cours entiers et sur leurs contenus
        long likes = reactionRepository.countByTeacherAndType(teacherId, ResourceReaction.LIKE)
                + courseReactionRepository.countByTeacherAndType(teacherId, ResourceReaction.LIKE);
        long dislikes = reactionRepository.countByTeacherAndType(teacherId, ResourceReaction.DISLIKE)
                + courseReactionRepository.countByTeacherAndType(teacherId, ResourceReaction.DISLIKE);

        return new TeacherProfile(teacher.getId(), teacher.getName(), teacher.getCreatedAt(),
                courses.size(), students.size(), enrollments,
                averagePercent(evaluations), evaluations.size(), buckets(evaluations),
                average(ratings), ratings.size(), satisfaction(ratings), distribution(ratings),
                likes, dislikes, likes + dislikes == 0 ? null : (int) Math.round(likes * 100.0 / (likes + dislikes)),
                courseList,
                ratings.stream().limit(6).map(r -> review(r, r.getCourse())).toList(),
                evaluations.stream().limit(6).map(FeedbackService::evaluation).toList());
    }

    // ── Calculs ─────────────────────────────────────────────────

    private RatingSummary summary(Course course, User user) {
        List<CourseRating> ratings = ratingRepository.findByCourseIdOrderByCreatedAtDesc(course.getId());
        MyRating mine = ratings.stream().filter(r -> r.getStudent().getId().equals(user.getId())).findFirst()
                .map(r -> new MyRating(r.getStars(), r.getComment(), r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt()))
                .orElse(null);
        return new RatingSummary(course.getId(), average(ratings), ratings.size(), distribution(ratings), satisfaction(ratings),
                mine, isStudent(user) && isEnrolled(user, course));
    }

    private TeacherRatingSummary teacherSummary(User teacher, User viewer) {
        List<TeacherRating> list = teacherRatingRepository.findByTeacherIdOrderByCreatedAtDescIdDesc(teacher.getId());
        MyTeacherRating mine = list.stream().filter(r -> r.getStudent().getId().equals(viewer.getId())).findFirst()
                .map(r -> new MyTeacherRating(r.getPercent(), r.getComment(), r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt()))
                .orElse(null);
        return new TeacherRatingSummary(teacher.getId(), averagePercent(list), list.size(), buckets(list), mine,
                canRateTeacher(viewer, teacher));
    }

    private ReactionSummary courseReactionSummary(Course course, User user) {
        boolean enrolledStudent = isStudent(user) && isEnrolled(user, course);
        String mine = courseReactionRepository.findByCourseIdAndUserId(course.getId(), user.getId())
                .map(CourseReaction::getType).orElse(null);
        return new ReactionSummary(course.getId(),
                courseReactionRepository.countByCourseIdAndType(course.getId(), ResourceReaction.LIKE),
                courseReactionRepository.countByCourseIdAndType(course.getId(), ResourceReaction.DISLIKE),
                mine, courseCommentRepository.countByCourseId(course.getId()),
                enrolledStudent, enrolledStudent || isCourseTeacher(user, course));
    }

    private ReactionSummary resourceReactionSummary(LessonResource resource, User user) {
        Course course = courseOf(resource);
        boolean enrolledStudent = isStudent(user) && isEnrolled(user, course);
        String mine = reactionRepository.findByResourceIdAndUserId(resource.getId(), user.getId())
                .map(ResourceReaction::getType).orElse(null);
        return new ReactionSummary(resource.getId(),
                reactionRepository.countByResourceIdAndType(resource.getId(), ResourceReaction.LIKE),
                reactionRepository.countByResourceIdAndType(resource.getId(), ResourceReaction.DISLIKE),
                mine, commentRepository.countByResourceId(resource.getId()),
                enrolledStudent, enrolledStudent || isCourseTeacher(user, course));
    }

    private CommentView commentView(Long id, User author, String content, LocalDateTime createdAt, User viewer, Course course) {
        boolean mine = author.getId().equals(viewer.getId());
        return new CommentView(id, author.getId(), author.getName(), author.getRole() != null ? author.getRole().getName() : null,
                Avatars.url(author), isCourseTeacher(author, course), content, createdAt, mine,
                mine || isCourseTeacher(viewer, course) || isAdmin(viewer));
    }

    private static Review review(CourseRating r, Course course) {
        return new Review(r.getId(), r.getStudent().getName(), course.getId(), course.getTitle(), r.getStars(), r.getComment(),
                r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt());
    }

    private static Evaluation evaluation(TeacherRating r) {
        return new Evaluation(r.getId(), r.getStudent().getName(), r.getPercent(), r.getComment(),
                r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt());
    }

    /** Moyenne exacte des étoiles, arrondie à 0,1. */
    private static Double average(List<CourseRating> ratings) {
        return ratings.isEmpty() ? null : round1(ratings.stream().mapToInt(CourseRating::getStars).average().orElse(0));
    }

    /** Moyenne exacte des évaluations en %, arrondie à 0,1. */
    private static Double averagePercent(List<TeacherRating> ratings) {
        return ratings.isEmpty() ? null : round1(ratings.stream().mapToInt(TeacherRating::getPercent).average().orElse(0));
    }

    /** Satisfaction des cours = somme des étoiles / (nombre de notes × 5), en %, sans arrondi intermédiaire. */
    private static Integer satisfaction(List<CourseRating> ratings) {
        if (ratings.isEmpty()) return null;
        long sum = ratings.stream().mapToLong(CourseRating::getStars).sum();
        return (int) Math.round(sum * 100.0 / (ratings.size() * 5.0));
    }

    private static long[] distribution(List<CourseRating> ratings) {
        long[] d = new long[5];
        ratings.forEach(r -> d[r.getStars() - 1]++);
        return d;
    }

    /** 0-20 %, 21-40 %, 41-60 %, 61-80 %, 81-100 %. */
    private static long[] buckets(List<TeacherRating> ratings) {
        long[] b = new long[5];
        ratings.forEach(r -> b[r.getPercent() <= 0 ? 0 : Math.min(4, (r.getPercent() - 1) / 20)]++);
        return b;
    }

    // ── Notifications ───────────────────────────────────────────

    private void notifyTeacherOfComment(Course course, User author, String where, String content) {
        notificationService.notifyStaff("💬 Nouveau commentaire de " + author.getName(),
                "Sur " + where + " : « " + content + " »", "ADMIN_FEEDBACK", course.getId());
        if (course.getTeacher() == null) return;
        notificationService.sendNotification(course.getTeacher(), "💬 Nouveau commentaire de " + author.getName(),
                "Sur " + where + " : « " + content + " »", "COMMENT", course.getId());
    }

    /** Réponse du formateur : prévient (une fois) chaque étudiant qui a commenté au même endroit. */
    private void notifyCommentersOfReply(List<User> commenters, User teacher, Course course, String where, String content) {
        commenters.stream()
                .filter(u -> !u.getId().equals(teacher.getId()))
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a)).values()
                .forEach(u -> notificationService.sendNotification(u, "💬 Réponse du formateur",
                        teacher.getName() + " a répondu sur " + where + " : « " + content + " »", "COMMENT_REPLY", course.getId()));
    }

    // ── Contrôles d'accès ───────────────────────────────────────

    private void requireAccess(User user, Course course) {
        if (!isEnrolled(user, course) && !isCourseTeacher(user, course) && !isAdmin(user)) {
            throw new SecurityException("Accès réservé aux étudiants inscrits et au formateur du cours.");
        }
    }

    private void requireEnrolledStudent(User user, Course course, String message) {
        if (!isStudent(user) || !isEnrolled(user, course)) throw new SecurityException(message);
    }

    /** @return true si l'auteur est le formateur du cours */
    private boolean requireCanComment(User author, Course course) {
        boolean teacher = isCourseTeacher(author, course);
        if (!teacher && !(isStudent(author) && isEnrolled(author, course))) {
            throw new SecurityException("Seuls les étudiants inscrits et le formateur du cours peuvent commenter.");
        }
        return teacher;
    }

    private void requireCanDelete(User user, User author, Course course) {
        if (!author.getId().equals(user.getId()) && !isCourseTeacher(user, course) && !isAdmin(user)) {
            throw new SecurityException("Vous ne pouvez supprimer que vos propres commentaires.");
        }
    }

    private boolean canRateTeacher(User student, User teacher) {
        return isStudent(student) && enrollmentRepository.existsStudentTeacherLink(student.getId(), teacher.getId());
    }

    private boolean isEnrolled(User user, Course course) {
        return enrollmentRepository.findByStudentIdAndCourseId(user.getId(), course.getId()).isPresent();
    }

    private static boolean isCourseTeacher(User user, Course course) {
        return course.getTeacher() != null && course.getTeacher().getId().equals(user.getId());
    }

    private static boolean isStudent(User user) {
        return user.getRole() != null && "STUDENT".equals(user.getRole().getName());
    }

    private static boolean isAdmin(User user) {
        String role = user.getRole() != null ? user.getRole().getName() : "";
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    // ── Outils ──────────────────────────────────────────────────

    private static String reactionType(ReactionRequest request) {
        String type = request == null || request.type() == null ? null : request.type().toUpperCase();
        if (!ResourceReaction.LIKE.equals(type) && !ResourceReaction.DISLIKE.equals(type)) {
            throw new IllegalArgumentException("Réaction invalide : LIKE ou DISLIKE.");
        }
        return type;
    }

    private static Course courseOf(LessonResource resource) {
        return resource.getLesson().getChapter().getCourse();
    }

    private static String resourceTitle(LessonResource r) {
        if (r.getTitle() != null && !r.getTitle().isBlank()) return r.getTitle();
        return switch (r.getResourceType().toString()) {
            case "PDF" -> "Document PDF";
            case "VIDEO" -> "Vidéo";
            case "AUDIO" -> "Audio";
            case "YOUTUBE_VIDEO" -> "Vidéo YouTube";
            case "URL", "WEB_PAGE" -> "Lien";
            case "TEXT" -> "Texte";
            default -> "Ressource";
        };
    }

    private static String stars(int n) {
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        if (t.length() > MAX_TEXT) throw new IllegalArgumentException("Texte trop long (" + MAX_TEXT + " caractères maximum).");
        return t;
    }

    private static String requireText(CommentRequest request) {
        String content = clean(request == null ? null : request.content());
        if (content == null) throw new IllegalArgumentException("Le commentaire est vide.");
        return content;
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }

    private User findTeacher(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() != null && "TEACHER".equals(u.getRole().getName()))
                .orElseThrow(() -> new IllegalArgumentException("Formateur introuvable."));
    }

    private Course findCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
    }

    private LessonResource findResource(Long id) {
        return resourceRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ressource introuvable."));
    }
}
