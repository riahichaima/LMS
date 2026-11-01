package com.course.lms.repository;

import com.course.lms.model.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    boolean existsByStudentIdAndLessonId(Long studentId, Long lessonId);

    long countByStudentIdAndCourseId(Long studentId, Long courseId);

    @Query("select lp.lesson.id from LessonProgress lp where lp.student.id = :studentId and lp.courseId = :courseId")
    List<Long> findCompletedLessonIds(@Param("studentId") Long studentId, @Param("courseId") Long courseId);
}
