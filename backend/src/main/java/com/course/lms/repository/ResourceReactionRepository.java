package com.course.lms.repository;

import com.course.lms.model.ResourceReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResourceReactionRepository extends JpaRepository<ResourceReaction, Long> {

    Optional<ResourceReaction> findByResourceIdAndUserId(Long resourceId, Long userId);

    long countByResourceIdAndType(Long resourceId, String type);

    /** Réactions sur toutes les ressources des cours d'un formateur. */
    @Query("select count(r) from ResourceReaction r where r.resource.lesson.chapter.course.teacher.id = :teacherId and r.type = :type")
    long countByTeacherAndType(@Param("teacherId") Long teacherId, @Param("type") String type);
}
