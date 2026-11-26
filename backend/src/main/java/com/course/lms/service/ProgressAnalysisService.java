package com.course.lms.service;

import com.course.lms.dto.StudentRecommendationsResponse;
import com.course.lms.model.Submission;
import com.course.lms.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class ProgressAnalysisService {

    private final WebClient webClient;

    @Autowired
    private SubmissionRepository submissionRepository;

    public ProgressAnalysisService(@Qualifier("lmsAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Analyze student performance and generate recommendations using LMS_AI ProgressAgent
     */
    @org.springframework.scheduling.annotation.Async("aiTaskExecutor")
    public CompletableFuture<StudentRecommendationsResponse> analyzeStudentPerformanceAsync(Long studentId, Long courseId) {
        // Copies de l'étudiant pour CE cours seulement
        java.util.Set<Long> ofCourse = submissionRepository.findByCourseId(courseId).stream()
                .map(Submission::getId).collect(java.util.stream.Collectors.toSet());
        List<Submission> submissions = submissionRepository.findByStudentId(studentId).stream()
                .filter(s -> ofCourse.contains(s.getId())).toList();
        return CompletableFuture.completedFuture(analyze(submissions, studentId, courseId));
    }

    /** Administration : progression de tous les étudiants d'un cours (copies de quiz et de TP). */
    @org.springframework.scheduling.annotation.Async("aiTaskExecutor")
    public CompletableFuture<StudentRecommendationsResponse> analyzeCourseAsync(Long courseId) {
        return CompletableFuture.completedFuture(analyze(submissionRepository.findByCourseId(courseId), null, courseId));
    }

    private StudentRecommendationsResponse analyze(List<Submission> submissions, Long studentId, Long courseId) {
        try {
            // Prepare performance data for AI analysis
            Map<String, Object> performanceData = new HashMap<>();
            performanceData.put("student_id", studentId);
            performanceData.put("course_id", courseId);
            performanceData.put("total_submissions", submissions.size());

            // Calculate average score
            double totalScore = 0;
            double totalMaxScore = 0;
            Map<String, Integer> difficultyCounts = new HashMap<>();
            difficultyCounts.put("BEGINNER", 0);
            difficultyCounts.put("INTERMEDIATE", 0);
            difficultyCounts.put("ADVANCED", 0);

            for (Submission submission : submissions) {
                if (submission.getPoints() != null && submission.getMaxPoints() != null) {
                    totalScore += submission.getPoints();
                    totalMaxScore += submission.getMaxPoints();
                }
                String difficulty = null;
                if (submission.getAssignment() != null && submission.getAssignment().getDifficultyLevel() != null) {
                    difficulty = submission.getAssignment().getDifficultyLevel().toString();
                } else if (submission.getQuiz() != null && submission.getQuiz().getDifficultyLevel() != null) {
                    difficulty = submission.getQuiz().getDifficultyLevel().toString();
                }
                if (difficulty != null) {
                    difficultyCounts.merge(difficulty, 1, Integer::sum);
                }
            }

            double averageScore = totalMaxScore > 0 ? (totalScore / totalMaxScore) * 100 : 0;
            performanceData.put("average_score", averageScore);
            performanceData.put("difficulty_distribution", difficultyCounts);
            // Notes séparées par type : sans elles, l'agent croyait qu'aucun TP n'avait été rendu
            performanceData.putAll(scoresByType(submissions));

            // 3. Call LMS_AI ProgressAgent for analysis
            @SuppressWarnings("unchecked")
            Mono<Map> aiResponse = webClient.post()
                    .uri("/api/progress/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(performanceData)
                    .retrieve()
                    .bodyToMono(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = (Map<String, Object>) aiResponse.block();

            // 4. Parse and prepare response
            StudentRecommendationsResponse response = new StudentRecommendationsResponse();
            response.setStudentId(studentId);
            response.setCourseId(courseId);
            response.setRecommendationDate(LocalDateTime.now());
            response.setSubmissionCount(submissions.size());
            response.setStudentCount((int) submissions.stream().filter(s -> s.getStudent() != null)
                    .map(s -> s.getStudent().getId()).distinct().count());
            response.setAverageScore(Math.round(averageScore * 10) / 10.0);

            if (aiResult.containsKey("recommended_lessons")) {
                response.setRecommendedLessons((List<String>) aiResult.get("recommended_lessons"));
            }

            if (aiResult.containsKey("weak_areas")) {
                response.setWeakAreas((List<String>) aiResult.get("weak_areas"));
            }

            if (aiResult.containsKey("strong_areas")) {
                response.setStrongAreas((List<String>) aiResult.get("strong_areas"));
            }

            if (aiResult.containsKey("personalized_advice")) {
                response.setPersonalizedAdvice((String) aiResult.get("personalized_advice"));
            }

            if (aiResult.containsKey("recommended_level")) {
                response.setRecommendedLevel((String) aiResult.get("recommended_level"));
            }

            return response;

        } catch (Exception e) {
            StudentRecommendationsResponse errorResponse = new StudentRecommendationsResponse();
            errorResponse.setStudentId(studentId);
            errorResponse.setCourseId(courseId);
            // Les chiffres réels restent affichés même si l'agent IA ne répond pas
            errorResponse.setSubmissionCount(submissions.size());
            errorResponse.setStudentCount((int) submissions.stream().filter(s -> s.getStudent() != null)
                    .map(s -> s.getStudent().getId()).distinct().count());
            double pts = 0, max = 0;
            for (Submission s : submissions) {
                if (s.getPoints() != null && s.getMaxPoints() != null) { pts += s.getPoints(); max += s.getMaxPoints(); }
            }
            errorResponse.setAverageScore(max > 0 ? Math.round(pts / max * 1000) / 10.0 : 0.0);
            errorResponse.setPersonalizedAdvice("Analyse IA indisponible pour le moment : réessayez dans un instant.");
            return errorResponse;
        }
    }

    /**
     * Note de chaque copie notée, en pourcentage (une décimale), séparée en quiz et TP :
     * {@code quiz_scores} et {@code tp_scores}, comme les attend l'agent de recommandation du service IA.
     */
    static Map<String, List<Double>> scoresByType(List<Submission> submissions) {
        List<Double> quiz = new java.util.ArrayList<>();
        List<Double> tp = new java.util.ArrayList<>();
        for (Submission s : submissions) {
            if (s.getPoints() == null || s.getMaxPoints() == null || s.getMaxPoints() <= 0) continue;
            double percent = Math.round(s.getPoints() / s.getMaxPoints() * 1000) / 10.0;
            if (s.getQuiz() != null) quiz.add(percent);
            else if (s.getAssignment() != null) tp.add(percent);
        }
        Map<String, List<Double>> scores = new HashMap<>();
        scores.put("quiz_scores", quiz);
        scores.put("tp_scores", tp);
        return scores;
    }
}
