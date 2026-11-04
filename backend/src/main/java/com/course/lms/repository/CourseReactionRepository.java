package com.course.lms.repository;

import com.course.lms.model.CourseReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CourseReactionRepository extends JpaRepository<CourseReaction, Long> {

    Optional<CourseReaction> findByCourseIdAndUserId(Long courseId, Long userId);

    long countByCourseIdAndType(Long courseId, String type);

    @Query("select count(r) from CourseReaction r where r.course.teacher.id = :teacherId and r.type = :type")
    long countByTeacherAndType(@Param("teacherId") Long teacherId, @Param("type") String type);
}
