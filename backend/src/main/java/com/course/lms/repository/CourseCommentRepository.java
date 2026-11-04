package com.course.lms.repository;

import com.course.lms.model.CourseComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseCommentRepository extends JpaRepository<CourseComment, Long> {

    List<CourseComment> findByCourseIdOrderByCreatedAtAscIdAsc(Long courseId);

    long countByCourseId(Long courseId);
}
