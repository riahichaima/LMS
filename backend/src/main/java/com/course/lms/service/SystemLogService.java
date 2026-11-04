package com.course.lms.service;

import com.course.lms.model.SystemLog;
import com.course.lms.repository.SystemLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * Écriture du journal système. Transaction séparée (REQUIRES_NEW) : le log est conservé même
 * si l'action échoue, et une erreur de journalisation ne casse jamais l'action elle-même.
 */
@Service
public class SystemLogService {

    private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);
    private static final int RETENTION_DAYS = 90;

    @Autowired
    private SystemLogRepository repository;

    /** Événement métier : acteur et IP lus dans le contexte de la requête courante. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String level, String category, String action, String message) {
        record(level, category, action, message, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String level, String category, String action, String message, String actorEmail) {
        try {
            SystemLog entry = new SystemLog();
            entry.setLevel(level);
            entry.setCategory(category);
            entry.setAction(truncate(action, 120));
            entry.setMessage(truncate(message, 1000));
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean authenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
            entry.setActorEmail(truncate(actorEmail != null ? actorEmail : authenticated ? auth.getName() : null, 190));
            if (authenticated && actorEmail == null) entry.setActorRole(roleOf(auth));
            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIpAddress(clientIp(request));
                entry.setHttpMethod(request.getMethod());
                entry.setPath(truncate(request.getRequestURI(), 255));
            }
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Journal système : écriture impossible ({})", e.getMessage());
        }
    }

    /** Requête HTTP terminée (appelé par SystemLogFilter). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequest(String level, String category, String action, String message, Authentication auth,
                              HttpServletRequest request, int status, long durationMs) {
        try {
            SystemLog entry = new SystemLog();
            entry.setLevel(level);
            entry.setCategory(category);
            entry.setAction(truncate(action, 120));
            entry.setMessage(truncate(message, 1000));
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                entry.setActorEmail(truncate(auth.getName(), 190));
                entry.setActorRole(roleOf(auth));
            }
            entry.setIpAddress(clientIp(request));
            entry.setHttpMethod(request.getMethod());
            entry.setPath(truncate(request.getRequestURI(), 255));
            entry.setStatus(status);
            entry.setDurationMs(durationMs);
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Journal système : écriture impossible ({})", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        try {
            int purged = repository.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
            record(SystemLog.INFO, "SYSTEM", "Démarrage de l'application",
                    "Backend LMS démarré" + (purged > 0 ? " — " + purged + " log(s) de plus de " + RETENTION_DAYS + " jours supprimé(s)" : ""),
                    "system");
        } catch (Exception e) {
            log.warn("Journal système : initialisation impossible ({})", e.getMessage());
        }
    }

    private static String roleOf(Authentication auth) {
        // SUPER_ADMIN a aussi ROLE_ADMIN : on garde le rôle le plus élevé
        String best = null;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String r = a.getAuthority().replaceFirst("^ROLE_", "");
            if ("SUPER_ADMIN".equals(r)) return r;
            if (best == null || "ADMIN".equals(r)) best = r;
        }
        return best;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        // Boucle locale IPv6 affichée comme en IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";
        return truncate(ip, 64);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max - 1) + "…" : s);
    }
}
