package com.course.lms.repository;

import com.course.lms.model.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    Optional<AssessmentAttempt> findByStudentIdAndKindAndAssessmentId(Long studentId, String kind, Long assessmentId);

    /** Passages dont le temps est écoulé sans que l'étudiant ait validé (envoi automatique). */
    List<AssessmentAttempt> findBySubmittedAtIsNullAndKindInAndEndsAtBefore(Collection<String> kinds, Instant limit);

    @Modifying
    @Query("delete from AssessmentAttempt a where a.kind = :kind and a.assessmentId in :ids")
    void deleteByKindAndAssessmentIds(@Param("kind") String kind, @Param("ids") Collection<Long> ids);
}
