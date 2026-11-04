package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion par l'enseignant des quiz et TP générés par l'IA pour ses cours : suppression
 * (refusée dès qu'un étudiant les a rendus). Les quiz/TP préparés à la main sont importés en fichier (AssessmentFileService).
 */
@Service
@Transactional
public class ManualAssessmentService {

    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    private final com.course.lms.repository.AssessmentAttemptRepository attemptRepository;

    public ManualAssessmentService(UserRepository userRepository, QuizRepository quizRepository,
                                   AssignmentRepository assignmentRepository, SubmissionRepository submissionRepository,
                                   com.course.lms.repository.AssessmentAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.quizRepository = quizRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
    }

    public void deleteQuiz(String email, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new IllegalArgumentException("Quiz introuvable."));
        requireOwner(email, quiz.getChapter() != null ? quiz.getChapter() : quiz.getLesson().getChapter());
        if (!submissionRepository.findByQuizId(quizId).isEmpty()) {
            throw new IllegalStateException("Des étudiants ont déjà passé ce quiz : il ne peut plus être supprimé.");
        }
        // Passages commencés (sans copie) : supprimés avec le quiz
        attemptRepository.deleteByKindAndAssessmentIds(com.course.lms.model.AssessmentAttempt.QUIZ, java.util.List.of(quizId));
        quizRepository.delete(quiz);
    }

    public void deleteAssignment(String email, Long assignmentId) {
        Assignment a = assignmentRepository.findById(assignmentId).orElseThrow(() -> new IllegalArgumentException("TP introuvable."));
        requireOwner(email, a.getChapter() != null ? a.getChapter() : a.getLesson().getChapter());
        if (!submissionRepository.findByAssignmentId(assignmentId).isEmpty()) {
            throw new IllegalStateException("Des étudiants ont déjà rendu ce TP : il ne peut plus être supprimé.");
        }
        attemptRepository.deleteByKindAndAssessmentIds(com.course.lms.model.AssessmentAttempt.TP, java.util.List.of(assignmentId));
        assignmentRepository.delete(a);
    }

    private void requireOwner(String email, Chapter chapter) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        User teacher = chapter.getCourse() != null ? chapter.getCourse().getTeacher() : null;
        if (teacher == null || !teacher.getId().equals(user.getId())) {
            throw new SecurityException("Vous ne pouvez gérer que les quiz et TP de vos propres cours.");
        }
    }
}
