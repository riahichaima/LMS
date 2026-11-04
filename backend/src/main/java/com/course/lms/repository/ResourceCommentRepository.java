package com.course.lms.repository;

import com.course.lms.model.ResourceComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourceCommentRepository extends JpaRepository<ResourceComment, Long> {

    List<ResourceComment> findByResourceIdOrderByCreatedAtAscIdAsc(Long resourceId);

    long countByResourceId(Long resourceId);

    @Query("select c from ResourceComment c where c.resource.lesson.chapter.course.id = :courseId order by c.createdAt desc, c.id desc")
    List<ResourceComment> findByCourseId(@Param("courseId") Long courseId);
}
