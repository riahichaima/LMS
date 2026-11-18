package com.course.lms.repository;

import com.course.lms.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByAssignmentId(Long assignmentId);
    List<Submission> findByQuizId(Long quizId);
    List<Submission> findByStudentId(Long studentId);
    List<Submission> findByStudentIdAndAssignmentId(Long studentId, Long assignmentId);
    List<Submission> findByStudentIdAndQuizId(Long studentId, Long quizId);

    /** Copies (quiz et TP) rendues pour un cours : le quiz ou le TP est rattaché au chapitre ou à une leçon du cours. */
    @Query("select s from Submission s left join s.quiz q left join s.assignment a "
            + "left join q.chapter qc left join q.lesson ql left join ql.chapter qlc "
            + "left join a.chapter ac left join a.lesson al left join al.chapter alc "
            + "where qc.course.id = :courseId or qlc.course.id = :courseId or ac.course.id = :courseId or alc.course.id = :courseId")
    List<Submission> findByCourseId(@Param("courseId") Long courseId);
}
