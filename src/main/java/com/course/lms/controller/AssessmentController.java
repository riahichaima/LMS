package com.course.lms.controller;

import com.course.lms.dto.*;
import com.course.lms.model.DifficultyLevel;
import com.course.lms.service.AssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/assessments")
@CrossOrigin(origins = "*")
public class AssessmentController {

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private com.course.lms.service.CourseAccessService courseAccessService;

    /**
     * Generate assessments (Quiz and/or Assignment) for selected chapters
     * Teacher only endpoint
     */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssessmentGenerationResponse> generateAssessments(
            @RequestBody AssessmentGenerationRequest request) {
        return ResponseEntity.ok(assessmentService.generateAssessments(request));
    }

    @PostMapping("/generate-quiz")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssessmentGenerationResponse> generateQuiz(
            @RequestBody QuizGenerationRequest request) {
        return ResponseEntity.ok(assessmentService.generateQuiz(request));
    }

    @PostMapping("/generate-assignment")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssessmentGenerationResponse> generateAssignment(
            @RequestBody AssignmentGenerationRequest request) {
        return ResponseEntity.ok(assessmentService.generateAssignment(request));
    }

    /**
     * Get available quizzes for a lesson by difficulty level
     * Student and Teacher endpoint
     */
    @GetMapping("/quizzes/lesson/{lessonId}")
    public ResponseEntity<List<com.course.lms.model.Quiz>> getQuizzesByLesson(
            @PathVariable Long lessonId,
            @RequestParam DifficultyLevel difficultyLevel) {
        courseAccessService.requireLessonAccess(lessonId);
        List<com.course.lms.model.Quiz> quizzes = assessmentService.getQuizzesByLessonAndDifficulty(lessonId, difficultyLevel);
        return ResponseEntity.ok(quizzes);
    }

    /**
     * Get available assignments for a lesson by difficulty level
     * Student and Teacher endpoint
     */
    @GetMapping("/assignments/lesson/{lessonId}")
    public ResponseEntity<List<com.course.lms.model.Assignment>> getAssignmentsByLesson(
            @PathVariable Long lessonId,
            @RequestParam DifficultyLevel difficultyLevel) {
        courseAccessService.requireLessonAccess(lessonId);
        List<com.course.lms.model.Assignment> assignments = assessmentService.getAssignmentsByLessonAndDifficulty(lessonId, difficultyLevel);
        return ResponseEntity.ok(assignments);
    }

    /**
     * Submit assignment for AI correction
     * Student only endpoint
     */
    @PostMapping("/submissions/correct")
    @PreAuthorize("hasRole('STUDENT')")
    public CompletableFuture<ResponseEntity<SubmissionCorrectionResponse>> correctSubmission(
            @RequestBody SubmissionCorrectionRequest request) {
        return assessmentService.correctSubmissionAsync(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    /**
     * Submit quiz answers for AI correction
     * Student only endpoint
     */
    @PostMapping("/quizzes/{quizId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public CompletableFuture<ResponseEntity<SubmissionCorrectionResponse>> submitQuiz(
            @PathVariable Long quizId,
            @RequestParam Long studentId,
            @RequestBody String studentAnswers) {
        return assessmentService.correctQuizSubmissionAsync(quizId, studentId, studentAnswers)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    /**
     * Get available quizzes for a chapter by difficulty level
     */
    @GetMapping("/quizzes/chapter/{chapterId}")
    public ResponseEntity<List<com.course.lms.model.Quiz>> getQuizzesByChapter(
            @PathVariable Long chapterId,
            @RequestParam DifficultyLevel difficultyLevel) {
        courseAccessService.requireChapterAccess(chapterId);
        List<com.course.lms.model.Quiz> quizzes = assessmentService.getQuizzesByChapterAndDifficulty(chapterId, difficultyLevel);
        return ResponseEntity.ok(quizzes);
    }

    /**
     * Get available assignments for a chapter by difficulty level
     */
    @GetMapping("/assignments/chapter/{chapterId}")
    public ResponseEntity<List<com.course.lms.model.Assignment>> getAssignmentsByChapter(
            @PathVariable Long chapterId,
            @RequestParam DifficultyLevel difficultyLevel) {
        courseAccessService.requireChapterAccess(chapterId);
        List<com.course.lms.model.Assignment> assignments = assessmentService.getAssignmentsByChapterAndDifficulty(chapterId, difficultyLevel);
        return ResponseEntity.ok(assignments);
    }

    /**
     * Student view quiz WITHOUT correct answers
     */
    @GetMapping("/quizzes/{quizId}/view")
    public ResponseEntity<com.course.lms.dto.QuizStudentResponse> viewQuizForStudent(@PathVariable Long quizId) {
        courseAccessService.requireQuizAccess(quizId);
        return ResponseEntity.ok(assessmentService.getQuizForStudent(quizId));
    }

    /**
     * Tous les quiz et TP de l'enseignant, par cours et par chapitre, avec corrections
     */
    @GetMapping("/teacher/overview")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<java.util.Map<String, Object>>> teacherAssessmentsOverview(
            org.springframework.security.core.Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(assessmentService.getTeacherAssessmentsOverview(authentication.getName(), isAdmin));
    }

    /**
     * Quiz et TP générés pour un chapitre, avec leurs corrections (vue enseignant)
     */
    @GetMapping("/chapter/{chapterId}/review")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> reviewChapterAssessments(@PathVariable Long chapterId) {
        return ResponseEntity.ok(assessmentService.getChapterAssessmentsForTeacher(chapterId));
    }

    /**
     * Teacher view quiz WITH correct answers and explanations
     */
    @GetMapping("/quizzes/{quizId}/detail")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<com.course.lms.dto.QuizDetailResponse> viewQuizDetailForTeacher(@PathVariable Long quizId) {
        return ResponseEntity.ok(assessmentService.getQuizDetailForTeacher(quizId));
    }

    /**
     * Student view correction AFTER submission
     */
    @GetMapping("/quizzes/{quizId}/correction/{submissionId}")
    public ResponseEntity<com.course.lms.dto.QuizCorrectionResponse> viewQuizCorrection(
            @PathVariable Long quizId, @PathVariable Long submissionId) {
        return ResponseEntity.ok(assessmentService.getQuizCorrection(quizId, submissionId));
    }
}
