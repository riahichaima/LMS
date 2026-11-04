package com.course.lms.service;

import com.course.lms.model.Chapter;
import com.course.lms.model.Lesson;
import com.course.lms.model.Quiz;
import com.course.lms.model.AssessmentAttempt;
import com.course.lms.repository.AssessmentAttemptRepository;
import com.course.lms.repository.AssignmentRepository;
import com.course.lms.repository.LessonProgressRepository;
import com.course.lms.repository.QuizRepository;
import com.course.lms.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Nettoyage avant la suppression d'un chapitre ou d'une leçon (suppression directe ou modification du cours) :
 * leçons terminées par les étudiants, quiz et TP générés et leurs copies, qui référencent ces contenus.
 * L'avancement des ressources et les sujets importés sont supprimés en cascade par la base.
 */
@Service
@Transactional
public class ContentCleanupService {

    private final LessonProgressRepository lessonProgressRepository;
    private final QuizRepository quizRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssessmentAttemptRepository attemptRepository;

    public ContentCleanupService(LessonProgressRepository lessonProgressRepository, QuizRepository quizRepository,
                                 AssignmentRepository assignmentRepository, SubmissionRepository submissionRepository,
                                 AssessmentAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.quizRepository = quizRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
    }

    public void beforeDeletingChapter(Chapter chapter) {
        beforeDeletingLessons(chapter.getLessons());
        quizRepository.findByChapterId(chapter.getId()).forEach(this::deleteQuiz);
        assignmentRepository.findByChapterId(chapter.getId()).forEach(this::deleteAssignment);
    }

    public void beforeDeletingLessons(Collection<Lesson> lessons) {
        List<Long> ids = lessons.stream().map(Lesson::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        lessonProgressRepository.deleteByLessonIds(ids);
        for (Long id : ids) {
            quizRepository.findByLessonId(id).forEach(this::deleteQuiz);
            assignmentRepository.findByLessonId(id).forEach(this::deleteAssignment);
        }
    }

    private void deleteAssignment(com.course.lms.model.Assignment a) {
        attemptRepository.deleteByKindAndAssessmentIds(AssessmentAttempt.TP, List.of(a.getId()));
        submissionRepository.deleteAll(submissionRepository.findByAssignmentId(a.getId()));
        assignmentRepository.delete(a);
    }

    private void deleteQuiz(Quiz quiz) {
        attemptRepository.deleteByKindAndAssessmentIds(AssessmentAttempt.QUIZ, List.of(quiz.getId()));
        submissionRepository.deleteAll(submissionRepository.findByQuizId(quiz.getId()));
        quizRepository.delete(quiz);
    }
}
