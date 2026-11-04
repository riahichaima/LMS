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

    @Autowired
    private com.course.lms.service.AssessmentScheduleService scheduleService;

    @Autowired
    private com.course.lms.repository.UserRepository userRepository;

    /**
     * Generate assessments (Quiz and/or Assignment) for selected chapters
     * Teacher only endpoint
     */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssessmentGenerationResponse> generateAssessments(
            @RequestBody AssessmentGenerationRequest request,
            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(assessmentService.generateAssessments(request, auth.getName()));
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
            @RequestParam(required = false) Long studentId,
            @RequestBody String studentAnswers,
            org.springframework.security.core.Authentication auth) {
        // L'étudiant est celui du jeton (le paramètre studentId n'est plus utilisé pour l'identité)
        com.course.lms.model.User student = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        if (studentId != null && !studentId.equals(student.getId())) {
            throw new SecurityException("Tu ne peux rendre un quiz que pour toi-même.");
        }
        courseAccessService.requireQuizAccess(quizId);
        // Date limite / chrono : après la fin du temps, ce sont les réponses enregistrées qui sont corrigées
        String answers = scheduleService.beginSubmission(student, "QUIZ", quizId, studentAnswers);
        Long sid = student.getId();
        return assessmentService.correctQuizSubmissionAsync(quizId, sid, answers)
                .thenApply(response -> {
                    if (Boolean.TRUE.equals(response.getSuccess())) scheduleService.markSubmitted(sid, "QUIZ", quizId, false);
                    return ResponseEntity.ok(response);
                });
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
    public ResponseEntity<com.course.lms.dto.QuizStudentResponse> viewQuizForStudent(@PathVariable Long quizId,
            org.springframework.security.core.Authentication auth) {
        courseAccessService.requireQuizAccess(quizId);
        // Quiz minuté : les questions ne sont visibles qu'une fois le chrono lancé
        userRepository.findByEmail(auth.getName()).ifPresent(u -> scheduleService.requireStartedIfTimed(u, "QUIZ", quizId));
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
