package com.course.lms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class LmsAiConfig {

    @Value("${lms-ai.base-url:http://localhost:8000}")
    private String baseUrl;

    // Video analysis (download + transcription + frames) can take several minutes
    @Value("${lms-ai.timeout-seconds:900}")
    private long timeoutSeconds;

    @Bean(name = "lmsAiWebClient")
    public WebClient lmsAiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
