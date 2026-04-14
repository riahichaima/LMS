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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private JwtAuthEntryPoint jwtAuthEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public JwtFilter jwtFilter() {
        return new JwtFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthEntryPoint))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/auth/verify").permitAll()
                .requestMatchers("/auth/forgot-password").permitAll()
                .requestMatchers("/auth/reset-password").permitAll()
                // Endpoints publics spécifiques (doivent être en premier!)
                .requestMatchers("/auth/register").permitAll()
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/teacher/register").permitAll()
                .requestMatchers("/teacher/login").permitAll()
                .requestMatchers("/student/register").permitAll()
                .requestMatchers("/student/login").permitAll()
                .requestMatchers("/admin/register").permitAll()
                .requestMatchers("/admin/login").permitAll()
                // Endpoints du module d'apprentissage (publics pour tous authentifiés)
                .requestMatchers("/api/courses/**").authenticated()
                .requestMatchers("/api/chapters/**").authenticated()
                .requestMatchers("/api/lessons/**").authenticated()
                .requestMatchers("/api/files/download/**").authenticated()
                // Endpoints spécifiques par rôle
                .requestMatchers("/admin/me").hasRole("ADMIN")
                .requestMatchers("/admin/users").hasRole("ADMIN")
                .requestMatchers("/admin/users/**").hasRole("ADMIN")
                .requestMatchers("/admin/roles-summary").hasRole("ADMIN")
                .requestMatchers("/teacher/me").hasRole("TEACHER")
                .requestMatchers("/teacher/me").hasRole("TEACHER")
                .requestMatchers("/student/me").hasRole("STUDENT")
                .requestMatchers("/student/me").hasRole("STUDENT")
                // Endpoints professeur uniquement
                .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                // Endpoints database et debug (admin uniquement)
                .requestMatchers("/database/**").hasRole("ADMIN")
                .requestMatchers("/debug/**").hasRole("ADMIN")
                // Auth endpoints
                .requestMatchers("/auth/me").authenticated()
                .requestMatchers("/auth/users").authenticated()
                .anyRequest().authenticated()
            );

        // Ajouter le JWT filter avec le bean injecté
        http.addFilterBefore(jwtFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
