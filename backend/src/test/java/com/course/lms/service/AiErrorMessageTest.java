package com.course.lms.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** L'enseignant lit l'explication du service IA, pas « 422 Unprocessable Entity ». */
class AiErrorMessageTest {

    private static WebClientResponseException aiError(int status, String body) {
        return WebClientResponseException.create(status, "Erreur", HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void showsTheAiExplanation() {
        String detail = "Le contenu du cours n'a pas pu être lu (Chapitre 1). Vérifiez que les fichiers sont bien déposés.";
        assertThat(AssessmentService.aiErrorMessage(aiError(422, "{\"detail\":\"" + detail + "\"}"), "Error generating assessments"))
                .isEqualTo(detail);
    }

    @Test
    void readableFallbackWhenBodyIsNotJson() {
        assertThat(AssessmentService.aiErrorMessage(aiError(502, "<html>Bad gateway</html>"), "Error generating quiz"))
                .contains("erreur 502").doesNotContain("<html>");
    }

    @Test
    void otherErrorsKeepTheirType() {
        assertThat(AssessmentService.aiErrorMessage(new IllegalStateException("boom"), "Error generating quiz"))
                .isEqualTo("Error generating quiz: [IllegalStateException] boom");
    }
}
