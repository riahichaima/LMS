package com.course.lms.service;

import com.course.lms.dto.MessagingDtos.NotificationView;
import com.course.lms.model.Notification;
import com.course.lms.model.User;
import com.course.lms.repository.NotificationRepository;
import com.course.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Notifications persistantes + push temps réel (WebSocket /user/queue/notifications).
 * Types : MESSAGE, ENROLLMENT, PAYMENT, SALE, WITHDRAWAL, ADMIN_REQUEST.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void sendNotification(User recipient, String title, String message, String type, Long relatedId) {
        String text = message == null ? "" : (message.length() > 300 ? message.substring(0, 297) + "…" : message);
        Notification saved = notificationRepository.save(new Notification(recipient, title, text, type, relatedId));
        pushToUser(recipient.getEmail(), "/queue/notifications", toView(saved));
    }

    /** Notifie tous les comptes d'un rôle (ex. ADMIN pour une demande de retrait). */
    @Transactional
    public void notifyRole(String roleName, String title, String message, String type, Long relatedId) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && roleName.equals(u.getRole().getName()))
                .forEach(u -> sendNotification(u, title, message, type, relatedId));
    }

    /** Notifie toute l'administration : administrateurs et super administrateur. */
    @Transactional
    public void notifyStaff(String title, String message, String type, Long relatedId) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && ("ADMIN".equals(u.getRole().getName()) || "SUPER_ADMIN".equals(u.getRole().getName())))
                .filter(u -> !"PENDING".equals(u.getApprovalStatus()))
                .forEach(u -> sendNotification(u, title, message, type, relatedId));
    }

    /**
     * Push WebSocket après le commit de la transaction en cours : le client qui recharge
     * ses données en recevant l'événement voit bien la ligne enregistrée.
     */
    public void pushToUser(String email, String destination, Object payload) {
        Runnable push = () -> {
            try {
                messagingTemplate.convertAndSendToUser(email, destination, payload);
            } catch (Exception e) {
                log.warn("Push WebSocket impossible vers {} : {}", email, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { push.run(); }
            });
        } else {
            push.run();
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationView> getMyNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(userId).stream()
                .limit(100).map(NotificationService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationView> getMyUnreadNotifications(Long userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationService::toView).toList();
    }

    /** Seul le destinataire peut marquer sa notification comme lue. */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(userId))
                .ifPresent(n -> n.setIsRead(true));
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDescIdDesc(userId)
                .forEach(n -> n.setIsRead(true));
    }

    /** Les notifications « nouveau message » d'une conversation ouverte deviennent lues. */
    @Transactional
    public void markMessageNotificationsAsRead(Long userId, Long senderId) {
        notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(n -> "MESSAGE".equals(n.getType()) && senderId.equals(n.getRelatedId()))
                .forEach(n -> n.setIsRead(true));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    private static NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.getRelatedId(),
                n.getCreatedAt(), Boolean.TRUE.equals(n.getIsRead()));
    }
}
