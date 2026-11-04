package com.course.lms.repository;

import com.course.lms.model.AssessmentFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AssessmentFileRepository extends JpaRepository<AssessmentFile, Long> {
    List<AssessmentFile> findByTeacherIdOrderByCreatedAtDescIdDesc(Long teacherId);
    List<AssessmentFile> findByCourseIdOrderByCreatedAtAscIdAsc(Long courseId);
    List<AssessmentFile> findByCourseIdIn(Collection<Long> courseIds);
}
