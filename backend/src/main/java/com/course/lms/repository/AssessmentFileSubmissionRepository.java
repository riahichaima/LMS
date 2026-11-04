package com.course.lms.repository;

import com.course.lms.model.AssessmentFileSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentFileSubmissionRepository extends JpaRepository<AssessmentFileSubmission, Long> {
    List<AssessmentFileSubmission> findByAssessmentFileIdOrderBySubmittedAtDesc(Long fileId);
    Optional<AssessmentFileSubmission> findByAssessmentFileIdAndStudentId(Long fileId, Long studentId);
    List<AssessmentFileSubmission> findByStudentId(Long studentId);
    long countByAssessmentFileId(Long fileId);
}
