package com.course.lms.controller;

import com.course.lms.dto.MessageRequest;
import com.course.lms.dto.MessageResponse;
import com.course.lms.dto.MessagingDtos.ContactView;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Messagerie interne Étudiant ↔ Formateur.
 *
 * REST (historique et envoi) + push WebSocket STOMP (temps réel, voir WebSocketConfig) :
 *   POST   /api/messages                      → envoyer un message (push /user/queue/messages)
 *   GET    /api/messages/contacts             → interlocuteurs autorisés, dernier message, non-lus
 *   GET    /api/messages/conversation/{id}    → historique (marque les messages reçus comme lus)
 *   GET    /api/messages/unread-count         → nombre de messages non lus
 *   GET    /api/messages/received | /sent     → messages reçus / envoyés
 *   PUT    /api/messages/{id}/read            → marquer un message comme lu
 */
@RestController
@RequestMapping("/api/messages")
@PreAuthorize("isAuthenticated()")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request, Authentication auth) {
        return ResponseEntity.ok(messageService.sendMessage(currentUserId(auth), request));
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactView>> getContacts(Authentication auth) {
        return ResponseEntity.ok(messageService.getContacts(currentUserId(auth)));
    }

    @GetMapping("/conversation/{otherUserId}")
    public ResponseEntity<List<MessageResponse>> getConversation(@PathVariable Long otherUserId, Authentication auth) {
        return ResponseEntity.ok(messageService.openConversation(currentUserId(auth), otherUserId));
    }

    @GetMapping("/received")
    public ResponseEntity<List<MessageResponse>> getReceivedMessages(Authentication auth) {
        return ResponseEntity.ok(messageService.getReceivedMessages(currentUserId(auth)));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<MessageResponse>> getSentMessages(Authentication auth) {
        return ResponseEntity.ok(messageService.getSentMessages(currentUserId(auth)));
    }

    @PutMapping("/{messageId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long messageId, Authentication auth) {
        messageService.markAsRead(messageId, currentUserId(auth));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication auth) {
        return ResponseEntity.ok(Map.of("unreadCount", messageService.countUnreadMessages(currentUserId(auth))));
    }

    private Long currentUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé: " + auth.getName()))
                .getId();
    }
}
