package com.course.lms.repository;

import com.course.lms.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDescIdDesc(Long recipientId);
    long countByRecipientIdAndIsReadFalse(Long recipientId);
}
