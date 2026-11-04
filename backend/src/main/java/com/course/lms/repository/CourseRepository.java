package com.course.lms.repository;

import com.course.lms.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    
    // Pour la pagination et la recherche par titre
    Page<Course> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    
    // Pour qu'un professeur puisse lister ses propres cours
    List<Course> findByTeacherId(Long teacherId);
}
