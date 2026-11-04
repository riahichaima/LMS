package com.course.lms.repository;

import com.course.lms.model.CourseRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRatingRepository extends JpaRepository<CourseRating, Long> {

    Optional<CourseRating> findByCourseIdAndStudentId(Long courseId, Long studentId);

    List<CourseRating> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    @Query("select r from CourseRating r where r.course.teacher.id = :teacherId order by r.createdAt desc, r.id desc")
    List<CourseRating> findByTeacherId(@Param("teacherId") Long teacherId);

    /** [courseId, moyenne, nombre] pour tous les cours notés (catalogue). */
    @Query("select r.course.id, avg(r.stars), count(r) from CourseRating r group by r.course.id")
    List<Object[]> summarizeAll();
}
