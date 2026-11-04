package com.course.lms.repository;

import com.course.lms.model.TeacherRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRatingRepository extends JpaRepository<TeacherRating, Long> {

    Optional<TeacherRating> findByTeacherIdAndStudentId(Long teacherId, Long studentId);

    List<TeacherRating> findByTeacherIdOrderByCreatedAtDescIdDesc(Long teacherId);
}
