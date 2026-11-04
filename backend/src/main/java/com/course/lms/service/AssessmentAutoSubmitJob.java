package com.course.lms.service;

import com.course.lms.dto.SubmissionCorrectionResponse;
import com.course.lms.model.AssessmentAttempt;
import com.course.lms.model.User;
import com.course.lms.repository.AssessmentAttemptRepository;
import com.course.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Temps écoulé sans validation (page fermée, connexion perdue...) : le quiz ou le TP est envoyé
 * automatiquement avec les réponses enregistrées. Si le service IA ne répond pas, on réessaie au tour suivant.
 */
@Component
public class AssessmentAutoSubmitJob {

    private static final Logger log = LoggerFactory.getLogger(AssessmentAutoSubmitJob.class);

    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentScheduleService scheduleService;
    private final AssessmentService assessmentService;
    private final StudentLearningService learningService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public AssessmentAutoSubmitJob(AssessmentAttemptRepository attemptRepository, AssessmentScheduleService scheduleService,
                                   AssessmentService assessmentService, StudentLearningService learningService,
                                   NotificationService notificationService, UserRepository userRepository) {
        this.attemptRepository = attemptRepository;
        this.scheduleService = scheduleService;
        this.assessmentService = assessmentService;
        this.learningService = learningService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Scheduled(fixedDelayString = "${app.assessments.auto-submit-check-ms:30000}", initialDelay = 25000)
    public void submitExpired() {
        Instant limit = Instant.now().minusSeconds(AssessmentScheduleService.GRACE_SECONDS);
        List<AssessmentAttempt> expired = attemptRepository.findBySubmittedAtIsNullAndKindInAndEndsAtBefore(
                List.of(AssessmentAttempt.QUIZ, AssessmentAttempt.TP), limit);
        for (AssessmentAttempt a : expired) {
            try {
                submit(a);
            } catch (Exception e) {
                log.warn("Envoi automatique impossible (passage {}) : {}", a.getId(), e.getMessage());
            }
        }
    }

    private void submit(AssessmentAttempt a) {
        Long studentId = a.getStudent().getId();
        User student = userRepository.findById(studentId).orElseThrow();
        String title;
        String score;
        if (AssessmentAttempt.QUIZ.equals(a.getKind())) {
            String answers = a.getDraft() != null && !a.getDraft().isBlank() ? a.getDraft() : "{}";
            SubmissionCorrectionResponse res = assessmentService.gradeQuiz(a.getAssessmentId(), studentId, answers);
            if (!Boolean.TRUE.equals(res.getSuccess())) throw new IllegalStateException("correction du quiz indisponible");
            title = scheduleService.rules(a.getKind(), a.getAssessmentId()).title();
            score = res.getScore() != null ? String.format("%.0f %%", res.getScore()) : "";
        } else {
            var result = learningService.autoSubmitAssignment(a.getAssessmentId(), student, a.getDraft());
            title = scheduleService.rules(a.getKind(), a.getAssessmentId()).title();
            score = result.get("points") + " / " + result.get("maxPoints");
        }
        scheduleService.markSubmitted(studentId, a.getKind(), a.getAssessmentId(), true);
        Long courseId = scheduleService.rules(a.getKind(), a.getAssessmentId()).course().getId();
        notificationService.sendNotification(student, "Temps écoulé",
                "« " + title + " » a été envoyé automatiquement avec tes réponses enregistrées. Note : " + score + ".",
                "ASSESSMENT_AUTO_SUBMITTED", courseId);
    }
}
