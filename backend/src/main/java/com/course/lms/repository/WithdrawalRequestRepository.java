package com.course.lms.repository;

import com.course.lms.model.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    List<WithdrawalRequest> findByTeacherIdOrderByRequestedAtDesc(Long teacherId);

    List<WithdrawalRequest> findAllByOrderByRequestedAtDesc();

    List<WithdrawalRequest> findByStatusInOrderByRequestedAtDesc(List<String> statuses);

    // SQL natif « FOR UPDATE » : compatible MySQL et MariaDB (XAMPP)
    @Query(value = "select * from withdrawal_requests where id = :id for update", nativeQuery = true)
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") Long id);
}
