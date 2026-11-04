package com.course.lms.controller;

import com.course.lms.dto.MessagingDtos.NotificationView;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Notifications de l'utilisateur connecté (temps réel : /user/queue/notifications). */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<NotificationView>> getMyNotifications(Authentication auth) {
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUserId(auth)));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationView>> getMyUnreadNotifications(Authentication auth) {
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications(currentUserId(auth)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication auth) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(currentUserId(auth))));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication auth) {
        notificationService.markAsRead(id, currentUserId(auth));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication auth) {
        notificationService.markAllAsRead(currentUserId(auth));
        return ResponseEntity.ok().build();
    }

    private Long currentUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé: " + auth.getName()))
                .getId();
    }
}
