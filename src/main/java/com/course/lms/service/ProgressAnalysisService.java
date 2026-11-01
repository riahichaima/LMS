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
        try {
            // 1. Retrieve student's submission history
            List<Submission> submissions = submissionRepository.findByStudentId(studentId);

            // 2. Prepare performance data for AI analysis
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

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            StudentRecommendationsResponse errorResponse = new StudentRecommendationsResponse();
            errorResponse.setStudentId(studentId);
            errorResponse.setCourseId(courseId);
            errorResponse.setPersonalizedAdvice("Error analyzing performance: " + e.getMessage());
            return CompletableFuture.completedFuture(errorResponse);
        }
    }
}
