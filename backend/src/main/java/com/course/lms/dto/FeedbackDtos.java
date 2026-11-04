package com.course.lms.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Avis des étudiants : note des cours (étoiles), évaluation des formateurs (%), réactions et
 * commentaires sur les cours et leurs ressources, profil formateur.
 */
public final class FeedbackDtos {

    private FeedbackDtos() {}

    public record RatingRequest(Integer stars, String comment) {}

    public record TeacherRatingRequest(Integer percent, String comment) {}

    public record ReactionRequest(String type) {}

    public record CommentRequest(String content) {}

    public record MyRating(int stars, String comment, LocalDateTime updatedAt) {}

    /** distribution[0] = nombre de notes à 1 étoile … distribution[4] = 5 étoiles. */
    public record RatingSummary(Long courseId, Double average, long count, long[] distribution,
                                Integer satisfactionPercent, MyRating myRating, boolean canRate) {}

    public record CourseRatingLine(Long courseId, Double average, long count) {}

    public record Review(Long id, String studentName, Long courseId, String courseTitle, int stars, String comment,
                         LocalDateTime date) {}

    public record MyTeacherRating(int percent, String comment, LocalDateTime updatedAt) {}

    /** buckets : 0-20 %, 21-40 %, 41-60 %, 61-80 %, 81-100 %. */
    public record TeacherRatingSummary(Long teacherId, Double average, long count, long[] buckets,
                                       MyTeacherRating myRating, boolean canRate) {}

    public record Evaluation(Long id, String studentName, int percent, String comment, LocalDateTime date) {}

    /** Réactions + commentaires d'un cours entier ou d'une ressource. */
    public record ReactionSummary(Long targetId, long likes, long dislikes, String myReaction, long commentCount,
                                  boolean canReact, boolean canComment) {}

    public record CommentView(Long id, Long authorId, String authorName, String authorRole, String authorAvatarUrl,
                              boolean fromTeacher, String content, LocalDateTime createdAt, boolean mine, boolean canDelete) {}

    /**
     * Commentaire vu par le formateur. scope = COURSE (commentaire sur le cours entier, resourceId null)
     * ou RESOURCE (sous un contenu précis).
     */
    public record CourseCommentView(Long id, String scope, Long resourceId, String resourceTitle, String resourceType,
                                    String lessonTitle, Long authorId, String authorName, boolean fromTeacher,
                                    String content, LocalDateTime createdAt) {}

    public record ProfileCourse(Long courseId, String title, String pricingType, long students, Double ratingAverage,
                                long ratingCount) {}

    public record TeacherProfile(Long teacherId, String name, LocalDateTime memberSince, long courses,
                                 long students, long enrollments,
                                 Double evaluationAverage, long evaluationCount, long[] evaluationBuckets,
                                 Double ratingAverage, long ratingCount, Integer satisfactionPercent, long[] distribution,
                                 long likes, long dislikes, Integer positivePercent,
                                 List<ProfileCourse> courseList, List<Review> recentReviews,
                                 List<Evaluation> recentEvaluations) {}
}
