package com.course.lms.controller;

import com.course.lms.dto.FeedbackDtos.*;
import com.course.lms.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Avis des étudiants et profil formateur :
 *   GET    /api/feedback/ratings                        → moyenne + nombre de notes par cours (catalogue)
 *   GET    /api/feedback/courses/{id}/rating            → résumé des étoiles + ma note
 *   PUT    /api/feedback/courses/{id}/rating            → noter le cours (étudiant inscrit) { stars, comment }
 *   DELETE /api/feedback/courses/{id}/rating            → retirer ma note
 *   GET    /api/feedback/courses/{id}/reviews           → avis du cours
 *   GET    /api/feedback/courses/{id}/feedback          → j'aime / je n'aime pas du cours + nombre de commentaires
 *   POST   /api/feedback/courses/{id}/reaction          → { type: LIKE | DISLIKE } (bascule)
 *   GET    /api/feedback/courses/{id}/discussion        → commentaires sur le cours entier
 *   POST   /api/feedback/courses/{id}/discussion        → { content }
 *   DELETE /api/feedback/course-comments/{id}           → supprimer (auteur, formateur du cours, admin)
 *   GET    /api/feedback/courses/{id}/comments          → tous les commentaires du cours (formateur / admin)
 *   GET    /api/feedback/resources/{id}                 → j'aime / je n'aime pas d'une ressource + nombre de commentaires
 *   POST   /api/feedback/resources/{id}/reaction        → { type: LIKE | DISLIKE } (bascule)
 *   GET    /api/feedback/resources/{id}/comments        → commentaires de la ressource
 *   POST   /api/feedback/resources/{id}/comments        → { content }
 *   DELETE /api/feedback/comments/{id}                  → supprimer un commentaire de ressource
 *   GET    /api/feedback/teachers/{id}/rating           → évaluation du formateur (%) + la mienne
 *   PUT    /api/feedback/teachers/{id}/rating           → évaluer le formateur (étudiant) { percent, comment }
 *   DELETE /api/feedback/teachers/{id}/rating           → retirer mon évaluation
 *   GET    /api/feedback/teachers/{id}/profile          → profil public du formateur
 */
@RestController
@RequestMapping("/api/feedback")
@PreAuthorize("isAuthenticated()")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    // ── Cours : étoiles ──
    @GetMapping("/ratings")
    public ResponseEntity<List<CourseRatingLine>> catalogRatings() {
        return ResponseEntity.ok(feedbackService.catalogRatings());
    }

    @GetMapping("/courses/{courseId}/rating")
    public ResponseEntity<RatingSummary> rating(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.ratingSummary(courseId, auth.getName()));
    }

    @PutMapping("/courses/{courseId}/rating")
    public ResponseEntity<RatingSummary> rate(@PathVariable Long courseId, @RequestBody RatingRequest request, Authentication auth) {
        return ResponseEntity.ok(feedbackService.rate(courseId, auth.getName(), request));
    }

    @DeleteMapping("/courses/{courseId}/rating")
    public ResponseEntity<RatingSummary> deleteRating(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.deleteRating(courseId, auth.getName()));
    }

    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<List<Review>> reviews(@PathVariable Long courseId) {
        return ResponseEntity.ok(feedbackService.reviews(courseId));
    }

    // ── Cours : j'aime / commentaires ──
    @GetMapping("/courses/{courseId}/feedback")
    public ResponseEntity<ReactionSummary> courseFeedback(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.courseFeedback(courseId, auth.getName()));
    }

    @PostMapping("/courses/{courseId}/reaction")
    public ResponseEntity<ReactionSummary> reactToCourse(@PathVariable Long courseId, @RequestBody ReactionRequest request, Authentication auth) {
        return ResponseEntity.ok(feedbackService.reactToCourse(courseId, auth.getName(), request));
    }

    @GetMapping("/courses/{courseId}/discussion")
    public ResponseEntity<List<CommentView>> courseDiscussion(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.courseDiscussion(courseId, auth.getName()));
    }

    @PostMapping("/courses/{courseId}/discussion")
    public ResponseEntity<CommentView> addCourseComment(@PathVariable Long courseId, @RequestBody CommentRequest request, Authentication auth) {
        return ResponseEntity.ok(feedbackService.addCourseComment(courseId, auth.getName(), request));
    }

    @DeleteMapping("/course-comments/{commentId}")
    public ResponseEntity<Void> deleteCourseComment(@PathVariable Long commentId, Authentication auth) {
        feedbackService.deleteCourseComment(commentId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses/{courseId}/comments")
    public ResponseEntity<List<CourseCommentView>> courseComments(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.courseComments(courseId, auth.getName()));
    }

    // ── Ressources ──
    @GetMapping("/resources/{resourceId}")
    public ResponseEntity<ReactionSummary> resource(@PathVariable Long resourceId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.resourceFeedback(resourceId, auth.getName()));
    }

    @PostMapping("/resources/{resourceId}/reaction")
    public ResponseEntity<ReactionSummary> react(@PathVariable Long resourceId, @RequestBody ReactionRequest request, Authentication auth) {
        return ResponseEntity.ok(feedbackService.react(resourceId, auth.getName(), request));
    }

    @GetMapping("/resources/{resourceId}/comments")
    public ResponseEntity<List<CommentView>> comments(@PathVariable Long resourceId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.comments(resourceId, auth.getName()));
    }

    @PostMapping("/resources/{resourceId}/comments")
    public ResponseEntity<CommentView> addComment(@PathVariable Long resourceId, @RequestBody CommentRequest request, Authentication auth) {
        return ResponseEntity.ok(feedbackService.addComment(resourceId, auth.getName(), request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId, Authentication auth) {
        feedbackService.deleteComment(commentId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // ── Formateurs ──
    @GetMapping("/teachers/{teacherId}/rating")
    public ResponseEntity<TeacherRatingSummary> teacherRating(@PathVariable Long teacherId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.teacherRatingSummary(teacherId, auth.getName()));
    }

    @PutMapping("/teachers/{teacherId}/rating")
    public ResponseEntity<TeacherRatingSummary> rateTeacher(@PathVariable Long teacherId, @RequestBody TeacherRatingRequest request,
                                                            Authentication auth) {
        return ResponseEntity.ok(feedbackService.rateTeacher(teacherId, auth.getName(), request));
    }

    @DeleteMapping("/teachers/{teacherId}/rating")
    public ResponseEntity<TeacherRatingSummary> deleteTeacherRating(@PathVariable Long teacherId, Authentication auth) {
        return ResponseEntity.ok(feedbackService.deleteTeacherRating(teacherId, auth.getName()));
    }

    @GetMapping("/teachers/{teacherId}/profile")
    public ResponseEntity<TeacherProfile> teacherProfile(@PathVariable Long teacherId) {
        return ResponseEntity.ok(feedbackService.teacherProfile(teacherId));
    }
}
