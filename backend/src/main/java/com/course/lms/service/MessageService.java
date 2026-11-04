package com.course.lms.service;

import com.course.lms.dto.MessageRequest;
import com.course.lms.dto.MessageResponse;
import com.course.lms.dto.MessagingDtos.ContactView;
import com.course.lms.dto.MessagingDtos.ReadReceipt;
import com.course.lms.model.Course;
import com.course.lms.model.Enrollment;
import com.course.lms.model.Message;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.MessageRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Messagerie interne Étudiant ↔ Formateur : envoi, historique, lecture, contacts.
 * Règle d'accès : un étudiant écrit aux formateurs de ses cours, un formateur aux étudiants
 * inscrits à ses cours. L'historique reste consultable si le lien disparaît (désinscription).
 */
@Service
@Transactional
public class MessageService {

    public static final int MAX_LENGTH = 2000;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private NotificationService notificationService;

    public MessageResponse sendMessage(Long senderId, MessageRequest request) {
        if (request.getReceiverId() == null) throw new IllegalArgumentException("Destinataire manquant");
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) throw new IllegalArgumentException("Le message est vide");
        if (content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Message trop long (" + MAX_LENGTH + " caractères maximum)");
        }

        User sender = findUser(senderId);
        User receiver = findUser(request.getReceiverId());
        if (!canMessage(sender, receiver)) {
            throw new SecurityException("Vous ne pouvez écrire qu'aux formateurs de vos cours ou aux étudiants inscrits à vos cours.");
        }

        MessageResponse response = toResponse(messageRepository.save(new Message(sender, receiver, content)));

        notificationService.sendNotification(receiver, "Nouveau message de " + sender.getName(), content, "MESSAGE", senderId);
        // Destinataire + expéditeur (synchronise ses autres onglets)
        notificationService.pushToUser(receiver.getEmail(), "/queue/messages", response);
        notificationService.pushToUser(sender.getEmail(), "/queue/messages", response);
        return response;
    }

    /** Historique de la conversation ; les messages reçus deviennent lus (accusé poussé à l'autre). */
    public List<MessageResponse> openConversation(Long userId, Long otherUserId) {
        User me = findUser(userId);
        User other = findUser(otherUserId);
        List<Message> messages = messageRepository.findConversation(userId, otherUserId);
        if (messages.isEmpty() && !canMessage(me, other)) {
            throw new SecurityException("Conversation non autorisée");
        }

        boolean changed = false;
        for (Message m : messages) {
            if (m.getReceiver().getId().equals(userId) && !m.isRead()) {
                m.setRead(true);
                changed = true;
            }
        }
        notificationService.markMessageNotificationsAsRead(userId, otherUserId);
        if (changed) {
            notificationService.pushToUser(other.getEmail(), "/queue/read", new ReadReceipt(userId, userId));
        }
        return messages.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversation(Long userId1, Long userId2) {
        return messageRepository.findConversation(userId1, userId2).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getReceivedMessages(Long userId) {
        return messageRepository.findByReceiverIdOrderBySentAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getSentMessages(Long userId) {
        return messageRepository.findBySenderIdOrderBySentAtDesc(userId).stream().map(this::toResponse).toList();
    }

    public void markAsRead(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message non trouvé: " + messageId));
        if (message.getReceiver().getId().equals(userId)) {
            message.setRead(true);
        }
    }

    public void markConversationAsRead(Long userId, Long otherUserId) {
        messageRepository.findConversation(userId, otherUserId).stream()
                .filter(m -> m.getReceiver().getId().equals(userId) && !m.isRead())
                .forEach(m -> m.setRead(true));
    }

    @Transactional(readOnly = true)
    public long countUnreadMessages(Long userId) {
        return messageRepository.countUnread(userId);
    }

    @Transactional(readOnly = true)
    public List<Long> getContactIds(Long userId) {
        return messageRepository.findContactIds(userId);
    }

    /**
     * Contacts autorisés (avec les cours en commun) + anciens interlocuteurs, triés par dernier message.
     */
    @Transactional(readOnly = true)
    public List<ContactView> getContacts(Long userId) {
        User me = findUser(userId);
        Map<Long, User> users = new LinkedHashMap<>();
        Map<Long, Set<String>> courses = new HashMap<>();

        if (hasRole(me, "STUDENT")) {
            for (Enrollment e : enrollmentRepository.findByStudentId(userId)) {
                User teacher = e.getCourse().getTeacher();
                if (teacher == null) continue;
                users.putIfAbsent(teacher.getId(), teacher);
                courses.computeIfAbsent(teacher.getId(), k -> new TreeSet<>()).add(e.getCourse().getTitle());
            }
        } else if (isStaff(me)) {
            // Administration : tous les étudiants et enseignants (et les autres administrateurs)
            for (User u : userRepository.findAll()) {
                if (u.getRole() != null && !u.getId().equals(userId) && u.isEnabled()) users.putIfAbsent(u.getId(), u);
            }
        } else if (hasRole(me, "TEACHER")) {
            for (Course c : courseRepository.findByTeacherId(userId)) {
                for (Enrollment e : enrollmentRepository.findByCourseId(c.getId())) {
                    User student = e.getStudent();
                    users.putIfAbsent(student.getId(), student);
                    courses.computeIfAbsent(student.getId(), k -> new TreeSet<>()).add(c.getTitle());
                }
            }
        }

        Map<Long, Message> lastByContact = new HashMap<>();
        Map<Long, Long> unreadByContact = new HashMap<>();
        for (Message m : messageRepository.findAllForUser(userId)) { // du plus récent au plus ancien
            boolean fromMe = m.getSender().getId().equals(userId);
            User other = fromMe ? m.getReceiver() : m.getSender();
            users.putIfAbsent(other.getId(), other);
            lastByContact.putIfAbsent(other.getId(), m);
            if (!fromMe && !m.isRead()) unreadByContact.merge(other.getId(), 1L, Long::sum);
        }
        users.remove(userId);

        return users.values().stream().map(u -> {
            Message last = lastByContact.get(u.getId());
            return new ContactView(u.getId(), u.getName(), u.getEmail(), u.getRole() != null ? u.getRole().getName() : null,
                    Avatars.url(u),
                    new ArrayList<>(courses.getOrDefault(u.getId(), Set.of())),
                    last != null ? last.getContent() : null,
                    last != null ? last.getSentAt() : null,
                    last != null && last.getSender().getId().equals(userId),
                    unreadByContact.getOrDefault(u.getId(), 0L));
        }).sorted(Comparator
                .comparing(ContactView::lastMessageAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                .thenComparing(c -> c.name() == null ? "" : c.name().toLowerCase())
        ).collect(Collectors.toList());
    }

    /** Étudiant ↔ formateur liés par au moins une inscription. */
    @Transactional(readOnly = true)
    public boolean canMessage(User a, User b) {
        if (a.getId().equals(b.getId())) return false;
        // L'administration peut écrire à tout le monde, et chacun peut lui répondre
        if (isStaff(a) || isStaff(b)) return true;
        if (hasRole(a, "STUDENT") && hasRole(b, "TEACHER")) {
            return enrollmentRepository.existsStudentTeacherLink(a.getId(), b.getId());
        }
        if (hasRole(a, "TEACHER") && hasRole(b, "STUDENT")) {
            return enrollmentRepository.existsStudentTeacherLink(b.getId(), a.getId());
        }
        return false;
    }

    private static boolean isStaff(User u) {
        return hasRole(u, "ADMIN") || hasRole(u, "SUPER_ADMIN");
    }

    private static boolean hasRole(User u, String role) {
        return u.getRole() != null && role.equals(u.getRole().getName());
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé: " + id));
    }

    private MessageResponse toResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setSenderId(message.getSender().getId());
        response.setSenderName(message.getSender().getName());
        response.setReceiverId(message.getReceiver().getId());
        response.setReceiverName(message.getReceiver().getName());
        response.setContent(message.getContent());
        response.setSentAt(message.getSentAt());
        response.setRead(message.isRead());
        return response;
    }
}
