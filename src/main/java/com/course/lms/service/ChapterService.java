package com.course.lms.service;

import com.course.lms.dto.ChapterRequest;
import com.course.lms.dto.ChapterResponse;
import com.course.lms.model.Chapter;
import com.course.lms.model.Course;
import com.course.lms.repository.ChapterRepository;
import com.course.lms.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChapterService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private CourseRepository courseRepository;

    public ChapterResponse createChapter(ChapterRequest request, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (!isAdmin && !course.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à ajouter un chapitre à ce cours");
        }

        Chapter chapter = new Chapter(request.getTitle(), course);
        Chapter savedChapter = chapterRepository.save(chapter);

        return mapToResponse(savedChapter);
    }

    public List<ChapterResponse> getChaptersByCourseId(Long courseId) {
        // Optionnel : vérifier si le cours existe d'abord
        if (!courseRepository.existsById(courseId)) {
            throw new RuntimeException("Cours non trouvé");
        }

        return chapterRepository.findByCourseId(courseId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ChapterResponse getChapterById(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new RuntimeException("Chapitre non trouvé"));
        
        return mapToResponse(chapter);
    }

    private ChapterResponse mapToResponse(Chapter chapter) {
        return new ChapterResponse(
                chapter.getId(),
                chapter.getTitle(),
                chapter.getCourse().getId()
        );
    }
}
