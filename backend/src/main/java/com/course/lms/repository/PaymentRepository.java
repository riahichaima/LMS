package com.course.lms.repository;

import com.course.lms.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripeSessionId(String stripeSessionId);

    // Verrou d'écriture : deux webhooks simultanés pour la même session ne peuvent pas confirmer deux fois
    // SQL natif « FOR UPDATE » : compatible MySQL et MariaDB (XAMPP)
    @Query(value = "select * from payments where stripe_session_id = :sessionId for update", nativeQuery = true)
    Optional<Payment> findByStripeSessionIdForUpdate(@Param("sessionId") String sessionId);

    @Query(value = "select * from payments where id = :id for update", nativeQuery = true)
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, String status);

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, String status);

    boolean existsByCourseIdAndStatus(Long courseId, String status);

    List<Payment> findByCourseIdAndStatusNot(Long courseId, String status);

    List<Payment> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Payment> findByTeacherIdAndStatusOrderByPaidAtDesc(Long teacherId, String status);

    List<Payment> findByStatusOrderByPaidAtDesc(String status);

    List<Payment> findAllByOrderByCreatedAtDesc();
}
