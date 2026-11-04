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

    // Niveau debug uniquement, et jamais le contenu du jeton
    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = request.getHeader("Authorization");
        
        
        String username = null;
        String jwt = null;

        // Ne traiter le JWT que si le header Authorization est présent
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                log.debug("Jeton refusé ({})", e.getClass().getSimpleName());
            }
        }

        // Ne procéder que si on a un username et pas d'authentification existante (ou seulement anonyme)
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (username != null && (existingAuth == null || !existingAuth.isAuthenticated() || 
            (existingAuth instanceof AnonymousAuthenticationToken))) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                
                if (!userDetails.isEnabled()) {
                    log.debug("Compte désactivé : authentification refusée");
                    // On ne définit pas l'authentification si le compte est désactivé
                } else if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = 
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                } else {
                    log.debug("Jeton invalide pour ce compte");
                }
            } catch (Exception e) {
                // Ignorer les erreurs pour les endpoints publics
                log.debug("Authentification impossible ({})", e.getClass().getSimpleName());
            }
        }
        
        // Toujours continuer le filter chain
        filterChain.doFilter(request, response);
    }
}
