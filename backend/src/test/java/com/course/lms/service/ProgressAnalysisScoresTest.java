package com.course.lms.service;

import com.course.lms.model.Assignment;
import com.course.lms.model.Quiz;
import com.course.lms.model.Submission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Les notes envoyées à l'agent de recommandation sont séparées en quiz et TP (sinon il croit qu'aucun TP n'est rendu). */
class ProgressAnalysisScoresTest {

    private static Submission copy(Quiz quiz, Assignment assignment, Double points, Double max) {
        Submission s = new Submission();
        s.setQuiz(quiz);
        s.setAssignment(assignment);
        s.setPoints(points);
        s.setMaxPoints(max);
        return s;
    }

    @Test
    void quizAndAssignmentScoresAreSentSeparately() {
        Quiz quiz = new Quiz();
        Assignment tp = new Assignment();
        Map<String, List<Double>> scores = ProgressAnalysisService.scoresByType(List.of(
                copy(quiz, null, 4.0, 5.0),
                copy(null, tp, 100.0, 100.0),
                copy(null, tp, 95.0, 100.0),
                copy(null, tp, null, 100.0),      // pas encore notée : ignorée
                copy(quiz, null, 3.0, 0.0)));     // barème nul : ignorée

        assertThat(scores.get("quiz_scores")).containsExactly(80.0);
        assertThat(scores.get("tp_scores")).containsExactly(100.0, 95.0);
    }

    @Test
    void noCopyGivesEmptyLists() {
        Map<String, List<Double>> scores = ProgressAnalysisService.scoresByType(List.of());
        assertThat(scores.get("quiz_scores")).isEmpty();
        assertThat(scores.get("tp_scores")).isEmpty();
    }
}
