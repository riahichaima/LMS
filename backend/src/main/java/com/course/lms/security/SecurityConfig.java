package com.course.lms.security;

import com.course.lms.security.jwt.JwtAuthEntryPoint;
import com.course.lms.security.jwt.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private JwtAuthEntryPoint jwtAuthEntryPoint;

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private com.course.lms.service.SystemLogService systemLogService;

    // Origines supplémentaires autorisées en production (ex. : https://lms.vercel.app), séparées par des virgules
    @org.springframework.beans.factory.annotation.Value("${app.cors-origins:}")
    private String extraCorsOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = new java.util.ArrayList<>(List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:8080"));
        Arrays.stream(extraCorsOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).forEach(origins::add);
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "Content-Disposition"));
        // JWT dans l'en-tête Authorization, aucun cookie : inutile d'autoriser les identifiants intersites
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthEntryPoint))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Les dispatchs ASYNC (endpoints Mono vers LMS_AI) et ERROR ont déjà été autorisés sur la requête initiale
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Health checks des hébergeurs (port public) et supervision (port de management interne)
                .requestMatchers("/livez", "/readyz").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/auth/verify").permitAll()
                .requestMatchers("/auth/forgot-password").permitAll()
                .requestMatchers("/auth/reset-password").permitAll()
                .requestMatchers("/auth/resend-verification").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                .requestMatchers("/auth/register").permitAll()
                .requestMatchers("/auth/login").permitAll()
                // À RÉACTIVER APRÈS LE DÉPLOIEMENT (connexion Google) :
                // .requestMatchers(HttpMethod.POST, "/auth/google").permitAll()
                // .requestMatchers(HttpMethod.GET, "/auth/google/config").permitAll()
                .requestMatchers("/teacher/register").permitAll()
                .requestMatchers("/student/register").permitAll()
                .requestMatchers("/admin/register").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/profiles/avatars/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/intelligent/ai-status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/intelligent/public/ai-status").permitAll()
                .requestMatchers("/api/courses/**").authenticated()
                .requestMatchers("/api/chapters/**").authenticated()
                .requestMatchers("/api/lessons/**").authenticated()
                .requestMatchers("/api/assessments/**").authenticated()
                .requestMatchers("/api/progress/**").authenticated()
                .requestMatchers("/api/files/download/**").authenticated()
                .requestMatchers("/api/messages/**").authenticated()
                .requestMatchers("/admin/me").hasRole("ADMIN")
                .requestMatchers("/admin/users").hasRole("ADMIN")
                .requestMatchers("/admin/users/**").hasRole("ADMIN")
                .requestMatchers("/admin/roles-summary").hasRole("ADMIN")
                .requestMatchers("/teacher/me").hasRole("TEACHER")
                .requestMatchers("/student/me").hasRole("STUDENT")
                .requestMatchers("/api/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/database/**").hasRole("ADMIN")
                .requestMatchers("/debug/**").hasRole("ADMIN")
                .requestMatchers("/auth/me").authenticated()
                .requestMatchers("/auth/users").authenticated()
                .anyRequest().authenticated()
            );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        // Journal système (supervision) : actions, refus d'accès et erreurs serveur
        http.addFilterBefore(new SystemLogFilter(systemLogService), JwtFilter.class);

        return http.build();
    }
}
