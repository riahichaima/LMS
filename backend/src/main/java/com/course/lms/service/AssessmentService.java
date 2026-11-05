package com.course.lms.service;

import com.course.lms.dto.*;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class AssessmentService {

    private final WebClient webClient;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private LessonResourceRepository lessonResourceRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssessmentScheduleService scheduleService;

    public AssessmentService(@Qualifier("lmsAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Generate assessments (Quiz and/or Assignment) for selected chapters using LMS_AI
     * Generates 3 versions for each difficulty level: BEGINNER, INTERMEDIATE, ADVANCED
     */
    public AssessmentGenerationResponse generateAssessments(AssessmentGenerationRequest request) {
        return generateAssessments(request, null);
    }

    /** teacherEmail : si renseigné, les chapitres doivent appartenir aux cours de cet enseignant. */
    public AssessmentGenerationResponse generateAssessments(AssessmentGenerationRequest request, String teacherEmail) {
        if (request.getChapterIds() == null || request.getChapterIds().isEmpty()) {
            throw new IllegalArgumentException("Choisissez au moins un chapitre.");
        }
        if (teacherEmail != null) {
            User teacher = userRepository.findByEmail(teacherEmail).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
            for (Long chapterId : request.getChapterIds()) {
                Chapter ch = chapterRepository.findById(chapterId).orElseThrow(() -> new IllegalArgumentException("Chapitre introuvable."));
                if (ch.getCourse().getTeacher() == null || !ch.getCourse().getTeacher().getId().equals(teacher.getId())) {
                    throw new SecurityException("Ce chapitre appartient au cours d'un autre enseignant.");
                }
            }
        }
        // Règles de rendu vérifiées avant d'appeler l'IA (pas de génération inutile)
        scheduleService.validateForFile(request.getDueAt(), request.getQuizDurationMinutes());
        scheduleService.validateForFile(request.getDueAt(), request.getAssignmentDurationMinutes());
        try {
            AssessmentGenerationResponse response = new AssessmentGenerationResponse();
            response.setGeneratedQuizzes(new ArrayList<>());
            response.setGeneratedAssignments(new ArrayList<>());

            // Get selected chapters
            List<Chapter> selectedChapters = new ArrayList<>();
            for (Long chapterId : request.getChapterIds()) {
                chapterRepository.findById(chapterId).ifPresent(selectedChapters::add);
            }

            // Get all lessons from selected chapters
            List<Lesson> lessons = new ArrayList<>();
            for (Chapter chapter : selectedChapters) {
                lessons.addAll(lessonRepository.findByChapterIdOrderByOrderIndexAsc(chapter.getId()));
            }

            // Collect all resources from these lessons (filtrées par type si l'enseignant a fait un choix)
            List<String> wantedTypes = request.getResourceTypes();
            List<LessonResource> resources = new ArrayList<>();
            for (Lesson lesson : lessons) {
                for (LessonResource resource : lessonResourceRepository.findByLessonId(lesson.getId())) {
                    if (wantedTypes == null || wantedTypes.isEmpty()
                            || wantedTypes.contains(resource.getResourceType().name())) {
                        resources.add(resource);
                    }
                }
            }
            if (resources.isEmpty() && wantedTypes != null && !wantedTypes.isEmpty()) {
                AssessmentGenerationResponse empty = new AssessmentGenerationResponse();
                empty.setSuccess(false);
                empty.setMessage("Aucune ressource de type " + String.join(", ", wantedTypes) + " dans ce chapitre.");
                return empty;
            }

            // Prepare content for LMS_AI with full lesson descriptions and resource data
            Map<String, Object> contentPayload = new HashMap<>();
            contentPayload.put("lessons", lessons.size());
            contentPayload.put("resources", resources.size());
            
            // Send lesson descriptions as content
            List<String> lessonDescriptions = new ArrayList<>();
            for (Lesson lesson : lessons) {
                StringBuilder lessonContent = new StringBuilder();
                lessonContent.append("Leçon: ").append(lesson.getTitle() != null ? lesson.getTitle() : "");
                if (lesson.getDescription() != null && !lesson.getDescription().isEmpty()) {
                    lessonContent.append(". ").append(lesson.getDescription());
                }
                lessonDescriptions.add(lessonContent.toString());
            }
            contentPayload.put("lesson_descriptions", lessonDescriptions);
            
            List<Map<String, String>> resourceDetails = new ArrayList<>();
            for (LessonResource resource : resources) {
                Map<String, String> detail = new HashMap<>();
                detail.put("type", resource.getResourceType().toString());
                if (resource.getFilePath() != null) detail.put("file_path", resource.getFilePath());
                if (resource.getExternalUrl() != null) detail.put("external_url", resource.getExternalUrl());
                if (resource.getTitle() != null) detail.put("title", resource.getTitle());
                if (resource.getMimeType() != null) detail.put("mime_type", resource.getMimeType());
                resourceDetails.add(detail);
            }
            contentPayload.put("resource_details", resourceDetails);
            contentPayload.put("generate_quiz", Boolean.TRUE.equals(request.getGenerateQuiz()));
            contentPayload.put("generate_assignment", Boolean.TRUE.equals(request.getGenerateAssignment()));
            contentPayload.put("quiz_question_count", request.getQuizQuestionCount() != null ? request.getQuizQuestionCount() : 5);
            contentPayload.put("assignment_exercise_count", request.getAssignmentExerciseCount() != null ? request.getAssignmentExerciseCount() : 3);
            contentPayload.put("difficulty_levels", request.getDifficultyLevels());

            // Call LMS_AI Orchestrator
            @SuppressWarnings("unchecked")
            Mono<Map> aiResponse = webClient.post()
                    .uri("/api/orchestrator/generate-assessments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(contentPayload)
                    .retrieve()
                    .bodyToMono(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = (Map<String, Object>) aiResponse.block();

            // Process AI response and save to database
            boolean generateQuiz = Boolean.TRUE.equals(request.getGenerateQuiz());
            boolean generateAssignment = Boolean.TRUE.equals(request.getGenerateAssignment());

            // Niveaux pour lesquels le modèle n'a rien produit (échec ou timeout) : rien n'est enregistré
            List<String> failed = new ArrayList<>();
            List<Quiz> savedQuizzes = new ArrayList<>();
            List<Assignment> savedAssignments = new ArrayList<>();

            if (generateQuiz && aiResult.containsKey("quizzes")) {
                List<Map<String, Object>> quizzes = (List<Map<String, Object>>) aiResult.get("quizzes");
                for (Map<String, Object> quizData : quizzes) {
                    List<?> generatedQuestions = (List<?>) quizData.get("questions");
                    if (generatedQuestions == null || generatedQuestions.isEmpty()) {
                        failed.add("quiz " + quizData.get("difficulty_level"));
                        continue;
                    }
                    Quiz quiz = new Quiz();
                    quiz.setTitle(scopedTitle("Quiz", (String) quizData.get("difficulty_level"), selectedChapters));
                    quiz.setDifficultyLevel(parseDifficultyLevel((String) quizData.get("difficulty_level")));
                    quiz.setQuestionCount((Integer) quizData.get("question_count"));
                    quiz.setTimeLimitMinutes((Integer) quizData.get("time_limit_minutes"));
                    quiz.setPassingScore((Double) quizData.get("passing_score"));
                    quiz.setGeneratedByAi(true);
                    
                    if (!lessons.isEmpty()) {
                        quiz.setLesson(lessons.get(0));
                    }
                    if (!selectedChapters.isEmpty()) {
                        quiz.setChapter(selectedChapters.get(0));
                    }
                    
                    Quiz savedQuiz = quizRepository.save(quiz);
                    savedQuizzes.add(savedQuiz);

                    // Save questions
                    if (quizData.containsKey("questions")) {
                        List<Map<String, Object>> questions = (List<Map<String, Object>>) quizData.get("questions");
                        for (Map<String, Object> questionData : questions) {
                            Question question = new Question();
                            question.setQuiz(savedQuiz);
                            question.setQuestionText((String) questionData.get("question_text"));
                            question.setQuestionType(parseQuestionType((String) questionData.get("question_type")));
                            question.setCorrectAnswer((String) questionData.get("correct_answer"));
                            question.setPoints((Integer) questionData.get("points"));
                            question.setQuestionOrder((Integer) questionData.get("question_order"));
                            question.setExplanationText((String) questionData.get("explanation_text"));
                            
                            Question savedQuestion = questionRepository.save(question);

                            // Save answers
                            if (questionData.containsKey("answers")) {
                                List<Map<String, Object>> answers = (List<Map<String, Object>>) questionData.get("answers");
                                for (Map<String, Object> answerData : answers) {
                                    Answer answer = new Answer();
                                    answer.setQuestion(savedQuestion);
                                    answer.setAnswerText((String) answerData.get("answer_text"));
                                    answer.setIsCorrect((Boolean) answerData.get("is_correct"));
                                    answer.setAnswerOrder((Integer) answerData.get("answer_order"));
                                    answerRepository.save(answer);
                                }
                            }
                        }
                    }

                    // Add to response
                    AssessmentGenerationResponse.GeneratedQuiz genQuiz = new AssessmentGenerationResponse.GeneratedQuiz();
                    genQuiz.setQuizId(savedQuiz.getId());
                    genQuiz.setTitle(savedQuiz.getTitle());
                    genQuiz.setDifficultyLevel(savedQuiz.getDifficultyLevel().toString());
                    genQuiz.setQuestionCount(savedQuiz.getQuestionCount());
                    response.getGeneratedQuizzes().add(genQuiz);
                }
            }

            if (generateAssignment && aiResult.containsKey("assignments")) {
                List<Map<String, Object>> assignments = (List<Map<String, Object>>) aiResult.get("assignments");
                for (Map<String, Object> assignmentData : assignments) {
                    List<?> generatedExercises = (List<?>) assignmentData.get("exercises");
                    if (generatedExercises == null || generatedExercises.isEmpty()) {
                        failed.add("TP " + assignmentData.get("difficulty_level"));
                        continue;
                    }
                    Assignment assignment = new Assignment();
                    assignment.setTitle(scopedTitle("TP", (String) assignmentData.get("difficulty_level"), selectedChapters));
                    assignment.setDifficultyLevel(parseDifficultyLevel((String) assignmentData.get("difficulty_level")));
                    assignment.setExerciseCount((Integer) assignmentData.get("exercise_count"));
                    assignment.setTimeLimitHours((Integer) assignmentData.get("time_limit_hours"));
                    assignment.setMaxPoints((Double) assignmentData.get("max_points"));
                    applyExercises(assignment, assignmentData);
                    assignment.setGeneratedByAi(true);
                    
                    if (!lessons.isEmpty()) {
                        assignment.setLesson(lessons.get(0));
                    }
                    if (!selectedChapters.isEmpty()) {
                        assignment.setChapter(selectedChapters.get(0));
                    }
                    
                    Assignment savedAssignment = assignmentRepository.save(assignment);
                    savedAssignments.add(savedAssignment);

                    // Add to response
                    AssessmentGenerationResponse.GeneratedAssignment genAssignment = new AssessmentGenerationResponse.GeneratedAssignment();
                    genAssignment.setAssignmentId(savedAssignment.getId());
                    genAssignment.setTitle(savedAssignment.getTitle());
                    genAssignment.setDifficultyLevel(savedAssignment.getDifficultyLevel().toString());
                    genAssignment.setExerciseCount(savedAssignment.getExerciseCount());
                    response.getGeneratedAssignments().add(genAssignment);
                }
            }

            boolean anyGenerated = !response.getGeneratedQuizzes().isEmpty() || !response.getGeneratedAssignments().isEmpty();
            response.setSuccess(anyGenerated);
            // Date limite et durée choisies par l'enseignant, puis notification des étudiants inscrits
            scheduleService.applyAfterGeneration(selectedChapters.isEmpty() ? null : selectedChapters.get(0).getCourse(),
                    savedQuizzes, savedAssignments, request.getDueAt(),
                    request.getQuizDurationMinutes(), request.getAssignmentDurationMinutes());
            if (failed.isEmpty()) {
                response.setMessage("Assessments generated successfully");
            } else if (anyGenerated) {
                response.setMessage("Génération partielle : le modèle IA n'a rien produit pour " + String.join(", ", failed) + ".");
            } else {
                response.setMessage("Le modèle IA n'a rien produit (" + String.join(", ", failed)
                        + "). Vérifiez que le chapitre contient du contenu (PDF, vidéo, lien) et réessayez.");
            }

            return response;

        } catch (Exception e) {
            logError(e);
            AssessmentGenerationResponse errorResponse = new AssessmentGenerationResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage(aiErrorMessage(e, "Error generating assessments"));
            return errorResponse;
        }
    }

    /**
     * Quiz et TP d'un chapitre pour l'enseignant, avec les corrections
     * (bonne réponse + explication pour les QCM, solution attendue pour les TP).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChapterAssessmentsForTeacher(Long chapterId) {
        List<com.course.lms.dto.QuizDetailResponse> quizzes = new ArrayList<>();
        for (Quiz quiz : quizRepository.findByChapterId(chapterId)) {
            if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) continue;
            quizzes.add(getQuizDetailForTeacher(quiz.getId()));
        }
        quizzes.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        List<Map<String, Object>> assignments = new ArrayList<>();
        for (Assignment assignment : assignmentRepository.findByChapterId(chapterId)) {
            if (assignment.getDescription() == null || assignment.getDescription().isBlank()) continue;
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", assignment.getId());
            dto.put("title", assignment.getTitle());
            dto.put("difficultyLevel", assignment.getDifficultyLevel() != null ? assignment.getDifficultyLevel().toString() : null);
            dto.put("exerciseCount", assignment.getExerciseCount());
            dto.put("maxPoints", assignment.getMaxPoints());
            dto.put("timeLimitHours", assignment.getTimeLimitHours());
            dto.put("createdAt", assignment.getCreatedAt());
            dto.put("statement", assignment.getDescription());
            dto.put("expectedSolution", assignment.getExpectedSolution());
            dto.put("generatedByAi", !Boolean.FALSE.equals(assignment.getGeneratedByAi()));
            dto.put("dueAt", assignment.getDueAt());
            dto.put("durationMinutes", assignment.getDurationMinutes());
            assignments.add(dto);
        }
        assignments.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id")));

        Map<String, Object> result = new HashMap<>();
        result.put("quizzes", quizzes);
        result.put("assignments", assignments);
        return result;
    }

    /**
     * Tous les quiz et TP générés par l'IA pour les cours de l'enseignant (tous les cours pour un admin),
     * regroupés par cours puis par chapitre, avec leurs corrections.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTeacherAssessmentsOverview(String email, boolean isAdmin) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        List<Course> courses = isAdmin ? courseRepository.findAll() : courseRepository.findByTeacherId(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Course course : courses) {
            List<Map<String, Object>> chapters = new ArrayList<>();
            for (Chapter chapter : chapterRepository.findByCourseIdOrderByOrderIndexAsc(course.getId())) {
                Map<String, Object> content = getChapterAssessmentsForTeacher(chapter.getId());
                if (((List<?>) content.get("quizzes")).isEmpty() && ((List<?>) content.get("assignments")).isEmpty()) continue;
                content.put("chapterId", chapter.getId());
                content.put("chapterTitle", chapter.getTitle());
                chapters.add(content);
            }
            if (chapters.isEmpty()) continue;
            Map<String, Object> dto = new HashMap<>();
            dto.put("courseId", course.getId());
            dto.put("courseTitle", course.getTitle());
            dto.put("chapters", chapters);
            result.add(dto);
        }
        return result;
    }

    /**
     * Generate ONLY Quiz for selected chapters/lessons with resource type filter
     */
    public AssessmentGenerationResponse generateQuiz(QuizGenerationRequest request) {
        try {
            AssessmentGenerationResponse response = new AssessmentGenerationResponse();
            response.setGeneratedQuizzes(new ArrayList<>());
            response.setGeneratedAssignments(new ArrayList<>());

            List<Lesson> lessons = resolveLessons(request.getLessonIds(), request.getChapterIds());
            List<Chapter> chapters = resolveChapters(request.getChapterIds());
            List<LessonResource> resources = filterResources(lessons, request.getResourceTypes());

            Map<String, Object> payload = buildLmsAiPayload(lessons, resources);
            payload.put("generate_quiz", true);
            payload.put("generate_assignment", false);
            payload.put("quiz_question_count", request.getQuestionCount() != null ? request.getQuestionCount() : 5);
            payload.put("difficulty_levels", request.getDifficultyLevels());

            Map<String, Object> aiResult = callLmsAi(payload);

            if (aiResult.containsKey("quizzes")) {
                saveQuizzes(aiResult, lessons, chapters, response);
            }

            response.setSuccess(true);
            response.setMessage("Quiz generated successfully");
            return response;
        } catch (Exception e) {
            logError(e);
            AssessmentGenerationResponse errorResponse = new AssessmentGenerationResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage(aiErrorMessage(e, "Error generating quiz"));
            return errorResponse;
        }
    }

    /**
     * Generate ONLY Assignment (TP) for selected chapters/lessons with resource type filter
     */
    public AssessmentGenerationResponse generateAssignment(AssignmentGenerationRequest request) {
        try {
            AssessmentGenerationResponse response = new AssessmentGenerationResponse();
            response.setGeneratedQuizzes(new ArrayList<>());
            response.setGeneratedAssignments(new ArrayList<>());

            List<Lesson> lessons = resolveLessons(request.getLessonIds(), request.getChapterIds());
            List<Chapter> chapters = resolveChapters(request.getChapterIds());
            List<LessonResource> resources = filterResources(lessons, request.getResourceTypes());

            Map<String, Object> payload = buildLmsAiPayload(lessons, resources);
            payload.put("generate_quiz", false);
            payload.put("generate_assignment", true);
            payload.put("assignment_exercise_count", request.getExerciseCount() != null ? request.getExerciseCount() : 3);
            payload.put("difficulty_levels", request.getDifficultyLevels());

            Map<String, Object> aiResult = callLmsAi(payload);

            if (aiResult.containsKey("assignments")) {
                saveAssignments(aiResult, lessons, chapters, response);
            }

            response.setSuccess(true);
            response.setMessage("Assignment generated successfully");
            return response;
        } catch (Exception e) {
            logError(e);
            AssessmentGenerationResponse errorResponse = new AssessmentGenerationResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage(aiErrorMessage(e, "Error generating assignment"));
            return errorResponse;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private List<Lesson> resolveLessons(List<Long> lessonIds, List<Long> chapterIds) {
        List<Lesson> lessons = new ArrayList<>();
        if (lessonIds != null && !lessonIds.isEmpty()) {
            for (Long id : lessonIds) {
                lessonRepository.findById(id).ifPresent(lessons::add);
            }
        } else if (chapterIds != null && !chapterIds.isEmpty()) {
            for (Long chapterId : chapterIds) {
                chapterRepository.findById(chapterId).ifPresent(c ->
                    lessons.addAll(lessonRepository.findByChapterIdOrderByOrderIndexAsc(c.getId())));
            }
        }
        return lessons;
    }

    private List<Chapter> resolveChapters(List<Long> chapterIds) {
        List<Chapter> chapters = new ArrayList<>();
        if (chapterIds != null) {
            for (Long id : chapterIds) {
                chapterRepository.findById(id).ifPresent(chapters::add);
            }
        }
        return chapters;
    }

    private List<LessonResource> filterResources(List<Lesson> lessons, List<ResourceType> resourceTypes) {
        List<LessonResource> allResources = new ArrayList<>();
        for (Lesson lesson : lessons) {
            allResources.addAll(lessonResourceRepository.findByLessonId(lesson.getId()));
        }
        if (resourceTypes != null && !resourceTypes.isEmpty()) {
            List<LessonResource> filtered = new ArrayList<>();
            for (LessonResource r : allResources) {
                if (resourceTypes.contains(r.getResourceType())) {
                    filtered.add(r);
                }
            }
            return filtered;
        }
        return allResources;
    }

    private Map<String, Object> buildLmsAiPayload(List<Lesson> lessons, List<LessonResource> resources) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lessons", lessons.size());
        payload.put("resources", resources.size());

        List<String> descriptions = new ArrayList<>();
        for (Lesson lesson : lessons) {
            StringBuilder sb = new StringBuilder("Lecon: ");
            sb.append(lesson.getTitle() != null ? lesson.getTitle() : "");
            if (lesson.getDescription() != null && !lesson.getDescription().isEmpty()) {
                sb.append(". ").append(lesson.getDescription());
            }
            descriptions.add(sb.toString());
        }
        payload.put("lesson_descriptions", descriptions);

        List<Map<String, String>> details = new ArrayList<>();
        for (LessonResource r : resources) {
            Map<String, String> d = new HashMap<>();
            d.put("type", r.getResourceType().toString());
            if (r.getFilePath() != null) d.put("file_path", r.getFilePath());
            if (r.getExternalUrl() != null) d.put("external_url", r.getExternalUrl());
            if (r.getTitle() != null) d.put("title", r.getTitle());
            if (r.getMimeType() != null) d.put("mime_type", r.getMimeType());
            details.add(d);
        }
        payload.put("resource_details", details);

        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callLmsAi(Map<String, Object> payload) {
        int maxRetries = 2;
        Exception lastException = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Mono<Map> aiResponse = webClient.post()
                        .uri("/api/orchestrator/generate-assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class);
                return (Map<String, Object>) aiResponse.block();
            } catch (Exception e) {
                lastException = e;
                System.out.println("callLmsAi attempt " + (attempt + 1) + " failed: " + e.getClass().getSimpleName() + " - " + (e.getMessage() != null ? e.getMessage() : "no message"));
                if (attempt < maxRetries - 1) {
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        if (lastException instanceof RuntimeException) throw (RuntimeException) lastException;
        throw new RuntimeException(lastException);
    }

    @SuppressWarnings("unchecked")
    private void saveQuizzes(Map<String, Object> aiResult, List<Lesson> lessons, List<Chapter> chapters,
                             AssessmentGenerationResponse response) {
        List<Map<String, Object>> quizzes = (List<Map<String, Object>>) aiResult.get("quizzes");
        for (Map<String, Object> quizData : quizzes) {
            Quiz quiz = new Quiz();
            quiz.setTitle((String) quizData.get("title"));
            quiz.setDifficultyLevel(parseDifficultyLevel((String) quizData.get("difficulty_level")));
            quiz.setQuestionCount((Integer) quizData.get("question_count"));
            quiz.setTimeLimitMinutes((Integer) quizData.get("time_limit_minutes"));
            quiz.setPassingScore((Double) quizData.get("passing_score"));
            quiz.setGeneratedByAi(true);
            if (!lessons.isEmpty()) quiz.setLesson(lessons.get(0));
            if (!chapters.isEmpty()) quiz.setChapter(chapters.get(0));

            Quiz savedQuiz = quizRepository.save(quiz);

            if (quizData.containsKey("questions")) {
                List<Map<String, Object>> questions = (List<Map<String, Object>>) quizData.get("questions");
                for (Map<String, Object> qData : questions) {
                    Question question = new Question();
                    question.setQuiz(savedQuiz);
                    question.setQuestionText((String) qData.get("question_text"));
                    question.setQuestionType(parseQuestionType((String) qData.get("question_type")));
                    question.setCorrectAnswer((String) qData.get("correct_answer"));
                    question.setPoints((Integer) qData.get("points"));
                    question.setQuestionOrder((Integer) qData.get("question_order"));
                    question.setExplanationText((String) qData.get("explanation_text"));

                    Question savedQuestion = questionRepository.save(question);

                    if (qData.containsKey("answers")) {
                        List<Map<String, Object>> answers = (List<Map<String, Object>>) qData.get("answers");
                        for (Map<String, Object> aData : answers) {
                            Answer answer = new Answer();
                            answer.setQuestion(savedQuestion);
                            answer.setAnswerText((String) aData.get("answer_text"));
                            answer.setIsCorrect((Boolean) aData.get("is_correct"));
                            answer.setAnswerOrder((Integer) aData.get("answer_order"));
                            answerRepository.save(answer);
                        }
                    }
                }
            }

            AssessmentGenerationResponse.GeneratedQuiz genQuiz = new AssessmentGenerationResponse.GeneratedQuiz();
            genQuiz.setQuizId(savedQuiz.getId());
            genQuiz.setTitle(savedQuiz.getTitle());
            genQuiz.setDifficultyLevel(savedQuiz.getDifficultyLevel().toString());
            genQuiz.setQuestionCount(savedQuiz.getQuestionCount());
            response.getGeneratedQuizzes().add(genQuiz);
        }
    }

    /**
     * Store the AI-generated exercises: statements go to the description (visible to students),
     * solutions go to expectedSolution (hidden, used for AI correction).
     */
    @SuppressWarnings("unchecked")
    private void applyExercises(Assignment assignment, Map<String, Object> assignmentData) {
        List<Map<String, Object>> exercises = (List<Map<String, Object>>) assignmentData.get("exercises");
        if (exercises == null || exercises.isEmpty()) return;

        StringBuilder statements = new StringBuilder();
        StringBuilder solutions = new StringBuilder();
        for (int i = 0; i < exercises.size(); i++) {
            Map<String, Object> exercise = exercises.get(i);
            // Markdown : un titre par exercice, l'énoncé et la solution contiennent des blocs ```code
            statements.append("### Exercice ").append(i + 1).append("\n\n").append(exercise.get("question")).append("\n\n");
            solutions.append("### Exercice ").append(i + 1).append("\n\n").append(exercise.get("expected_solution")).append("\n\n");
        }
        assignment.setDescription(statements.toString().trim());
        assignment.setExpectedSolution(solutions.toString().trim());
    }

    @SuppressWarnings("unchecked")
    private void saveAssignments(Map<String, Object> aiResult, List<Lesson> lessons, List<Chapter> chapters,
                                 AssessmentGenerationResponse response) {
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) aiResult.get("assignments");
        for (Map<String, Object> aData : assignments) {
            Assignment assignment = new Assignment();
            assignment.setTitle((String) aData.get("title"));
            assignment.setDifficultyLevel(parseDifficultyLevel((String) aData.get("difficulty_level")));
            assignment.setExerciseCount((Integer) aData.get("exercise_count"));
            assignment.setTimeLimitHours((Integer) aData.get("time_limit_hours"));
            assignment.setMaxPoints((Double) aData.get("max_points"));
            applyExercises(assignment, aData);
            assignment.setGeneratedByAi(true);
            if (!lessons.isEmpty()) assignment.setLesson(lessons.get(0));
            if (!chapters.isEmpty()) assignment.setChapter(chapters.get(0));

            Assignment saved = assignmentRepository.save(assignment);

            AssessmentGenerationResponse.GeneratedAssignment gen = new AssessmentGenerationResponse.GeneratedAssignment();
            gen.setAssignmentId(saved.getId());
            gen.setTitle(saved.getTitle());
            gen.setDifficultyLevel(saved.getDifficultyLevel().toString());
            gen.setExerciseCount(saved.getExerciseCount());
            response.getGeneratedAssignments().add(gen);
        }
    }

    private QuestionType parseQuestionType(String type) {
        if (type == null) return QuestionType.MULTIPLE_CHOICE;
        switch (type.toUpperCase()) {
            case "MCQ":
            case "MULTIPLE_CHOICE":
                return QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE":
            case "TF":
                return QuestionType.TRUE_FALSE;
            case "SHORT_ANSWER":
            case "SA":
                return QuestionType.SHORT_ANSWER;
            case "ESSAY":
                return QuestionType.ESSAY;
            default:
                return QuestionType.MULTIPLE_CHOICE;
        }
    }

    private DifficultyLevel parseDifficultyLevel(String level) {
        if (level == null) return DifficultyLevel.BEGINNER;
        switch (level.toUpperCase()) {
            case "BEGINNER":
            case "EASY":
            case "BASIC":
                return DifficultyLevel.BEGINNER;
            case "INTERMEDIATE":
            case "MEDIUM":
            case "MODERATE":
                return DifficultyLevel.INTERMEDIATE;
            case "ADVANCED":
            case "HARD":
            case "DIFFICULT":
            case "EXPERT":
                return DifficultyLevel.ADVANCED;
            default:
                return DifficultyLevel.BEGINNER;
        }
    }

    /**
     * Message montré à l'enseignant : l'explication renvoyée par le service IA (« detail », ex. contenu du cours
     * illisible) plutôt que « 422 Unprocessable Entity ».
     */
    static String aiErrorMessage(Exception e, String prefix) {
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wre) {
            try {
                var detail = new com.fasterxml.jackson.databind.ObjectMapper().readTree(wre.getResponseBodyAsString()).path("detail");
                if (detail.isTextual() && !detail.asText().isBlank()) return detail.asText();
            } catch (Exception ignored) {
                // corps non JSON : message générique ci-dessous
            }
            return "Le service IA n'a pas pu générer le contenu (erreur " + wre.getStatusCode().value() + "). Réessayez dans un instant.";
        }
        return prefix + ": [" + e.getClass().getSimpleName() + "] " + (e.getMessage() != null ? e.getMessage() : "no message");
    }

    private void logError(Exception e) {
        System.out.println("=== ASSESSMENT SERVICE ERROR ===");
        System.out.println("Type: " + e.getClass().getName());
        System.out.println("Message: " + e.getMessage());
        e.printStackTrace(System.out);
        System.out.println("================================");
    }

    /**
     * Correct student submission using LMS_AI AssessmentAgent
     */
    @org.springframework.scheduling.annotation.Async("aiTaskExecutor")
    public CompletableFuture<SubmissionCorrectionResponse> correctSubmissionAsync(SubmissionCorrectionRequest request) {
        try {
            Submission submission = submissionRepository.findById(request.getSubmissionId())
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new RuntimeException("Assignment not found"));

            // Prepare payload for LMS_AI
            Map<String, Object> payload = new HashMap<>();
            boolean hasStatement = assignment.getDescription() != null && !assignment.getDescription().isBlank();
            boolean hasSolution = assignment.getExpectedSolution() != null && !assignment.getExpectedSolution().isBlank();
            payload.put("question", hasStatement ? assignment.getDescription() : assignment.getTitle());
            payload.put("student_answer", request.getStudentAnswer());
            payload.put("expected_answer", hasSolution ? assignment.getExpectedSolution() : request.getAssignmentContent());
            payload.put("response_type", "text");
            payload.put("level", assignment.getDifficultyLevel().toString().toLowerCase());
            payload.put("user_id", request.getStudentId());

            // Call LMS_AI CorrectionAgent
            @SuppressWarnings("unchecked")
            Mono<Map> aiResponse = webClient.post()
                    .uri("/api/correct/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = (Map<String, Object>) aiResponse.block();

            // Update submission with AI correction results
            Object scoreObj = aiResult.get("score");
            double score = 0.0;
            if (scoreObj instanceof Number) {
                score = ((Number) scoreObj).doubleValue();
            }
            submission.setPoints(score);
            submission.setMaxPoints(assignment.getMaxPoints());
            submission.setFeedback((String) aiResult.get("feedback"));
            submission.setDetailedExplanation((String) aiResult.get("correction"));
            submission.setGradedByAi(true);
            submission.setGradedAt(LocalDateTime.now());
            submissionRepository.save(submission);

            // Prepare response
            SubmissionCorrectionResponse response = new SubmissionCorrectionResponse();
            response.setSuccess(true);
            response.setScore(submission.getPoints());
            response.setMaxScore(submission.getMaxPoints());
            response.setFeedback(submission.getFeedback());
            response.setDetailedExplanation(submission.getDetailedExplanation());
            response.setRecommendedLevel(assignment.getDifficultyLevel().toString());

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            SubmissionCorrectionResponse errorResponse = new SubmissionCorrectionResponse();
            errorResponse.setSuccess(false);
            return CompletableFuture.completedFuture(errorResponse);
        }
    }

    /**
     * Correct quiz submission using LMS_AI AssessmentAgent
     */
    @org.springframework.scheduling.annotation.Async("aiTaskExecutor")
    public CompletableFuture<SubmissionCorrectionResponse> correctQuizSubmissionAsync(Long quizId, Long studentId, String studentAnswers) {
        return CompletableFuture.completedFuture(gradeQuiz(quizId, studentId, studentAnswers));
    }

    /** Corrige et enregistre une copie de quiz (aussi utilisé pour l'envoi automatique à la fin du temps). */
    public SubmissionCorrectionResponse gradeQuiz(Long quizId, Long studentId, String studentAnswers) {
        try {
            Quiz quiz = quizRepository.findById(quizId)
                    .orElseThrow(() -> new RuntimeException("Quiz not found"));

            Map<String, Object> payload = new HashMap<>();
            payload.put("student_answers", studentAnswers);
            payload.put("quiz_title", quiz.getTitle());
            payload.put("difficulty_level", quiz.getDifficultyLevel().toString());
            payload.put("question_count", quiz.getQuestionCount());

            List<Map<String, Object>> questionsData = new ArrayList<>();
            for (Question question : questionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId)) {
                Map<String, Object> qData = new HashMap<>();
                qData.put("question_text", question.getQuestionText());
                qData.put("question_type", question.getQuestionType().toString());
                qData.put("correct_answer", question.getCorrectAnswer());
                qData.put("points", question.getPoints());
                qData.put("explanation_text", question.getExplanationText());
                questionsData.add(qData);
            }
            payload.put("questions", questionsData);

            @SuppressWarnings("unchecked")
            Mono<Map> aiResponse = webClient.post()
                    .uri("/api/correct/correct-quiz")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = (Map<String, Object>) aiResponse.block();

            User student = new User();
            student.setId(studentId);

            Submission submission = new Submission(quiz, student, studentAnswers);
            submission.setPoints((Double) aiResult.get("score"));
            submission.setMaxPoints((Double) aiResult.get("max_score"));
            submission.setFeedback((String) aiResult.get("feedback"));
            submission.setDetailedExplanation((String) aiResult.get("detailed_explanation"));
            submission.setGradedByAi(true);
            submission.setGradedAt(LocalDateTime.now());
            submissionRepository.save(submission);

            SubmissionCorrectionResponse response = new SubmissionCorrectionResponse();
            response.setSuccess(true);
            response.setScore(submission.getPoints());
            response.setMaxScore(submission.getMaxPoints());
            response.setFeedback(submission.getFeedback());
            response.setDetailedExplanation(submission.getDetailedExplanation());
            response.setRecommendedLevel((String) aiResult.get("recommended_level"));

            return response;

        } catch (Exception e) {
            SubmissionCorrectionResponse errorResponse = new SubmissionCorrectionResponse();
            errorResponse.setSuccess(false);
            return errorResponse;
        }
    }

    // « Quiz Débutant · Chapitre 2 » ou « TP Avancé · Tout le cours » selon le contenu choisi
    private String scopedTitle(String kind, String level, List<Chapter> chapters) {
        String label = switch (level == null ? "" : level.toUpperCase()) {
            case "BEGINNER" -> "Débutant";
            case "INTERMEDIATE" -> "Intermédiaire";
            case "ADVANCED" -> "Avancé";
            default -> level == null ? "" : level;
        };
        if (chapters.isEmpty()) return kind + " " + label;
        String scope;
        if (chapters.size() == 1) {
            scope = chapters.get(0).getTitle();
        } else {
            Course course = chapters.get(0).getCourse();
            int total = chapterRepository.findByCourseIdOrderByOrderIndexAsc(course.getId()).size();
            scope = chapters.size() >= total ? "Tout le cours" : chapters.size() + " chapitres";
        }
        String title = kind + " " + label + " · " + scope;
        return title.length() > 250 ? title.substring(0, 250) : title;
    }

    /**
     * Get available assessments for a lesson by difficulty level
     */
    public List<Quiz> getQuizzesByLessonAndDifficulty(Long lessonId, DifficultyLevel difficultyLevel) {
        return quizRepository.findByLessonIdAndDifficultyLevel(lessonId, difficultyLevel);
    }

    public List<Assignment> getAssignmentsByLessonAndDifficulty(Long lessonId, DifficultyLevel difficultyLevel) {
        return assignmentRepository.findByLessonIdAndDifficultyLevel(lessonId, difficultyLevel);
    }

    /**
     * Get available assessments for a chapter by difficulty level
     */
    public List<Quiz> getQuizzesByChapterAndDifficulty(Long chapterId, DifficultyLevel difficultyLevel) {
        return quizRepository.findByChapterIdAndDifficultyLevel(chapterId, difficultyLevel);
    }

    public List<Assignment> getAssignmentsByChapterAndDifficulty(Long chapterId, DifficultyLevel difficultyLevel) {
        return assignmentRepository.findByChapterIdAndDifficultyLevel(chapterId, difficultyLevel);
    }

    /**
     * Get quiz for student view (without correct answers)
     */
    public com.course.lms.dto.QuizStudentResponse getQuizForStudent(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        com.course.lms.dto.QuizStudentResponse response = new com.course.lms.dto.QuizStudentResponse();
        response.setId(quiz.getId());
        response.setTitle(quiz.getTitle());
        response.setDescription(quiz.getDescription());
        response.setDifficultyLevel(quiz.getDifficultyLevel().toString());
        response.setQuestionCount(quiz.getQuestionCount());
        response.setTimeLimitMinutes(quiz.getTimeLimitMinutes());
        response.setPassingScore(quiz.getPassingScore());

        List<Question> questions = questionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        List<com.course.lms.dto.QuizStudentResponse.QuestionWithoutAnswer> questionDtos = new ArrayList<>();

        for (Question q : questions) {
            com.course.lms.dto.QuizStudentResponse.QuestionWithoutAnswer qDto = 
                new com.course.lms.dto.QuizStudentResponse.QuestionWithoutAnswer();
            qDto.setId(q.getId());
            qDto.setQuestionText(q.getQuestionText());
            qDto.setQuestionType(q.getQuestionType().toString());
            qDto.setPoints(q.getPoints());
            qDto.setQuestionOrder(q.getQuestionOrder());

            List<com.course.lms.dto.QuizStudentResponse.AnswerChoice> answerDtos = new ArrayList<>();
            for (Answer a : q.getAnswers()) {
                com.course.lms.dto.QuizStudentResponse.AnswerChoice aDto = 
                    new com.course.lms.dto.QuizStudentResponse.AnswerChoice();
                aDto.setId(a.getId());
                aDto.setAnswerText(a.getAnswerText());
                aDto.setAnswerOrder(a.getAnswerOrder());
                answerDtos.add(aDto);
            }
            qDto.setAnswers(answerDtos);
            questionDtos.add(qDto);
        }
        response.setQuestions(questionDtos);
        return response;
    }

    /**
     * Get full correction for a quiz submission (viewable after completion)
     */
    public com.course.lms.dto.QuizCorrectionResponse getQuizCorrection(Long quizId, Long submissionId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (!submission.getQuiz().getId().equals(quizId)) {
            throw new RuntimeException("Submission does not belong to this quiz");
        }

        com.course.lms.dto.QuizCorrectionResponse response = new com.course.lms.dto.QuizCorrectionResponse();
        response.setSubmissionId(submission.getId());
        response.setQuizId(quiz.getId());
        response.setQuizTitle(quiz.getTitle());
        response.setStudentId(submission.getStudent().getId());
        response.setStudentName(submission.getStudent().getName());
        response.setScore(submission.getPoints());
        response.setMaxScore(submission.getMaxPoints());
        response.setFeedback(submission.getFeedback());
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setGradedAt(submission.getGradedAt());

        // Parse student answers from submission content
        String studentAnswersRaw = submission.getContent();
        java.util.Map<String, String> studentAnswerMap = new java.util.HashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, String> parsed = mapper.readValue(studentAnswersRaw, java.util.Map.class);
            if (parsed != null) studentAnswerMap = parsed;
        } catch (Exception e) {
            studentAnswerMap = new java.util.HashMap<>();
        }

        List<Question> questions = questionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        List<com.course.lms.dto.QuizCorrectionResponse.QuestionCorrection> correctionList = new ArrayList<>();

        for (Question q : questions) {
            com.course.lms.dto.QuizCorrectionResponse.QuestionCorrection qc = 
                new com.course.lms.dto.QuizCorrectionResponse.QuestionCorrection();
            qc.setQuestionText(q.getQuestionText());
            qc.setCorrectAnswer(q.getCorrectAnswer());
            qc.setPoints(q.getPoints());
            qc.setExplanation(q.getExplanationText());

            String studentAns = studentAnswerMap.get(String.valueOf(q.getQuestionOrder()));
            qc.setStudentAnswer(studentAns != null ? studentAns : "");
            qc.setIsCorrect(studentAns != null && studentAns.equalsIgnoreCase(q.getCorrectAnswer()));
            correctionList.add(qc);
        }
        response.setQuestions(correctionList);
        return response;
    }

    /**
     * Get quiz detail for teacher (WITH correct answers and explanations)
     */
    public com.course.lms.dto.QuizDetailResponse getQuizDetailForTeacher(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        com.course.lms.dto.QuizDetailResponse response = new com.course.lms.dto.QuizDetailResponse();
        response.setId(quiz.getId());
        response.setTitle(quiz.getTitle());
        response.setDescription(quiz.getDescription());
        response.setDifficultyLevel(quiz.getDifficultyLevel().toString());
        response.setQuestionCount(quiz.getQuestionCount());
        response.setTimeLimitMinutes(quiz.getTimeLimitMinutes());
        response.setPassingScore(quiz.getPassingScore());
        if (quiz.getLesson() != null) {
            response.setLessonTitle(quiz.getLesson().getTitle());
        }
        if (quiz.getChapter() != null) {
            response.setChapterTitle(quiz.getChapter().getTitle());
        }
        response.setGeneratedByAi(!Boolean.FALSE.equals(quiz.getGeneratedByAi()));
        response.setDueAt(quiz.getDueAt());
        response.setDurationMinutes(quiz.getDurationMinutes());

        List<Question> questions = questionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        List<com.course.lms.dto.QuizDetailResponse.QuestionDetail> questionDtos = new ArrayList<>();

        for (Question q : questions) {
            com.course.lms.dto.QuizDetailResponse.QuestionDetail qDto =
                new com.course.lms.dto.QuizDetailResponse.QuestionDetail();
            qDto.setId(q.getId());
            qDto.setQuestionText(q.getQuestionText());
            qDto.setQuestionType(q.getQuestionType().toString());
            qDto.setCorrectAnswer(q.getCorrectAnswer());
            qDto.setExplanation(q.getExplanationText());
            qDto.setPoints(q.getPoints());
            qDto.setQuestionOrder(q.getQuestionOrder());

            List<com.course.lms.dto.QuizDetailResponse.AnswerDetail> answerDtos = new ArrayList<>();
            for (Answer a : q.getAnswers()) {
                com.course.lms.dto.QuizDetailResponse.AnswerDetail aDto =
                    new com.course.lms.dto.QuizDetailResponse.AnswerDetail();
                aDto.setId(a.getId());
                aDto.setAnswerText(a.getAnswerText());
                aDto.setIsCorrect(a.getIsCorrect());
                aDto.setAnswerOrder(a.getAnswerOrder());
                answerDtos.add(aDto);
            }
            qDto.setAnswers(answerDtos);
            questionDtos.add(qDto);
        }
        response.setQuestions(questionDtos);
        return response;
    }
}
