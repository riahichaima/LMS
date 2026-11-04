package com.course.lms.repository;

import com.course.lms.model.Assignment;
import com.course.lms.model.DifficultyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByLessonId(Long lessonId);
    List<Assignment> findByLessonIdAndDifficultyLevel(Long lessonId, DifficultyLevel difficultyLevel);
    List<Assignment> findByChapterId(Long chapterId);
    List<Assignment> findByChapterIdAndDifficultyLevel(Long chapterId, DifficultyLevel difficultyLevel);
    List<Assignment> findByGeneratedByAiTrue();
}
