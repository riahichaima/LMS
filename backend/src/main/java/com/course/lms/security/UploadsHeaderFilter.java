package com.course.lms.security;

import com.course.lms.service.FileStorageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Fichiers publics /uploads : un fichier d'un type non accepté (ex. .html ou .svg déposé avant la liste blanche)
 * est servi en téléchargement et dans un bac à sable, donc son JavaScript ne s'exécute jamais sur le domaine de l'API.
 * Les supports normaux (PDF, vidéo, audio, image...) restent affichés comme avant.
 */
@Component
public class UploadsHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/uploads/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!FileStorageService.ALLOWED_EXTENSIONS.contains(extension(request.getRequestURI()))) {
            response.setHeader("Content-Disposition", "attachment");
            response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        }
        chain.doFilter(request, response);
    }

    private static String extension(String uri) {
        String name = uri.substring(uri.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
