package com.course.lms.repository;

import com.course.lms.model.DifficultyLevel;
import com.course.lms.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByLessonId(Long lessonId);
    List<Quiz> findByLessonIdAndDifficultyLevel(Long lessonId, DifficultyLevel difficultyLevel);
    List<Quiz> findByChapterId(Long chapterId);
    List<Quiz> findByChapterIdAndDifficultyLevel(Long chapterId, DifficultyLevel difficultyLevel);
    List<Quiz> findByGeneratedByAiTrue();
}
