package com.course.lms.repository;

import com.course.lms.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId);
    List<Complaint> findAllByOrderByCreatedAtDescIdDesc();
    long countByStatus(String status);
}
