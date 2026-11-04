package com.course.lms.repository;

import com.course.lms.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository pour les messages internes.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Récupère la conversation entre deux utilisateurs, triée par date.
     */
    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender.id = :userId1 AND m.receiver.id = :userId2) OR " +
           "(m.sender.id = :userId2 AND m.receiver.id = :userId1) " +
           "ORDER BY m.sentAt ASC, m.id ASC")
    List<Message> findConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Récupère tous les messages reçus par un utilisateur.
     */
    List<Message> findByReceiverIdOrderBySentAtDesc(Long receiverId);

    /**
     * Récupère tous les messages envoyés par un utilisateur.
     */
    List<Message> findBySenderIdOrderBySentAtDesc(Long senderId);

    /**
     * Compte les messages non lus pour un utilisateur.
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiver.id = :receiverId AND m.isRead = false")
    long countUnread(@Param("receiverId") Long receiverId);

    /**
     * Tous les messages d'un utilisateur (envoyés ou reçus), du plus récent au plus ancien.
     */
    @Query("SELECT m FROM Message m WHERE m.sender.id = :userId OR m.receiver.id = :userId ORDER BY m.sentAt DESC, m.id DESC")
    List<Message> findAllForUser(@Param("userId") Long userId);

    /**
     * Récupère la liste des contacts (utilisateurs avec qui on a échangé).
     */
    @Query("SELECT DISTINCT CASE WHEN m.sender.id = :userId THEN m.receiver.id ELSE m.sender.id END " +
           "FROM Message m WHERE m.sender.id = :userId OR m.receiver.id = :userId")
    List<Long> findContactIds(@Param("userId") Long userId);
}
