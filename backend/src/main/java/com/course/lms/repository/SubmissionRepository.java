package com.course.lms.repository;

import com.course.lms.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByAssignmentId(Long assignmentId);
    List<Submission> findByQuizId(Long quizId);
    List<Submission> findByStudentId(Long studentId);
    List<Submission> findByStudentIdAndAssignmentId(Long studentId, Long assignmentId);
    List<Submission> findByStudentIdAndQuizId(Long studentId, Long quizId);
}
