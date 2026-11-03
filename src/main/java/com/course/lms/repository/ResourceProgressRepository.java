package com.course.lms.repository;

import com.course.lms.model.ResourceProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceProgressRepository extends JpaRepository<ResourceProgress, Long> {
    Optional<ResourceProgress> findByStudentIdAndResourceId(Long studentId, Long resourceId);
    List<ResourceProgress> findByStudentIdAndCourseId(Long studentId, Long courseId);
}
