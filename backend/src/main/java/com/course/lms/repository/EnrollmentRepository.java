package com.course.lms.repository;

import com.course.lms.model.Enrollment;
import com.course.lms.model.Course;
import com.course.lms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<Enrollment> findByStudentId(Long studentId);

    List<Enrollment> findByCourseId(Long courseId);

    List<Enrollment> findByStudentIdAndIsCompleted(Long studentId, Boolean isCompleted);

    List<Enrollment> findByCourseIdAndIsCompleted(Long courseId, Boolean isCompleted);

    long countByCourseId(Long courseId);

    long countByStudentIdAndIsCompleted(Long studentId, Boolean isCompleted);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.student.id = :studentId AND e.course.teacher.id = :teacherId")
    boolean existsStudentTeacherLink(@org.springframework.data.repository.query.Param("studentId") Long studentId,
                                     @org.springframework.data.repository.query.Param("teacherId") Long teacherId);
}
