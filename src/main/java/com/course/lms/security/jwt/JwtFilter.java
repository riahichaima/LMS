package com.course.lms.security.jwt;

import com.course.lms.security.CustomUserDetailsService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = request.getHeader("Authorization");
        
        // Debug: afficher le path et le header Authorization
        System.out.println("JWT Filter - Request: " + request.getMethod() + " " + request.getRequestURI() + " - Auth: " + (authorizationHeader != null ? "present" : "absent"));
        
        String username = null;
        String jwt = null;

        // Ne traiter le JWT que si le header Authorization est présent
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            System.out.println("JWT Filter - Extracted JWT: " + jwt.substring(0, Math.min(jwt.length(), 50)) + "...");
            
            try {
                username = jwtUtil.extractUsername(jwt);
                System.out.println("JWT Filter - Extracted username: " + username);
            } catch (Exception e) {
                System.out.println("JWT Filter - Token validation FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace(System.out);
            }
        } else {
            System.out.println("JWT Filter - No Bearer token found");
        }

        // Ne procéder que si on a un username et pas d'authentification existante (ou seulement anonyme)
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (username != null && (existingAuth == null || !existingAuth.isAuthenticated() || 
            (existingAuth instanceof AnonymousAuthenticationToken))) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                System.out.println("JWT Filter - User loaded: " + userDetails.getUsername());
                
                if (!userDetails.isEnabled()) {
                    System.out.println("JWT Filter - Account is disabled for: " + username);
                    // On ne définit pas l'authentification si le compte est désactivé
                } else if (jwtUtil.validateToken(jwt, userDetails)) {
                    System.out.println("JWT Filter - Token validated successfully");
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = 
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    System.out.println("JWT Filter - Authentication set in context");
                } else {
                    System.out.println("JWT Filter - Token validation failed");
                }
            } catch (Exception e) {
                // Ignorer les erreurs pour les endpoints publics
                logger.debug("User authentication failed: " + e.getMessage());
                System.out.println("JWT Filter - User authentication failed: " + e.getMessage());
            }
        } else {
            System.out.println("JWT Filter - No username or existing authentication (existing: " + 
                (existingAuth != null ? existingAuth.getClass().getSimpleName() : "null") + 
                ", authenticated: " + (existingAuth != null && existingAuth.isAuthenticated()) + ")");
        }
        
        // Toujours continuer le filter chain
        filterChain.doFilter(request, response);
    }
}
