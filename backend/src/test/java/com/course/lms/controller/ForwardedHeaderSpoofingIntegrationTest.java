package com.course.lms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vrai serveur HTTP : un client ne peut pas changer d'IP aux yeux de la limite des essais en inventant
 * un en-tête « Forwarded » (RFC 7239). Les 20 échecs viennent de la même machine, donc le 21e est refusé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedHeaderSpoofingIntegrationTest {

    @LocalServerPort int port;

    @Test
    void anInventedForwardedHeaderDoesNotChangeTheCountedIp() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        int last = 0;
        for (int i = 1; i <= 21; i++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("Forwarded", "for=203.0.113." + i) // fausse IP différente à chaque essai
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"email\":\"spoof-" + i + "@test.lms\",\"password\":\"faux\"}"))
                    .build();
            last = http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            if (i <= 20) assertThat(last).as("essai %d", i).isEqualTo(401);
        }
        assertThat(last).isEqualTo(429);
    }
}
