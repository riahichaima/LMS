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

    @Autowired
    private LessonService lessonService;

    @Autowired
    private ContentCleanupService contentCleanupService;

    public ChapterResponse createChapter(ChapterRequest request, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (!isAdmin && !course.getTeacher().getEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("Non autorisé : ce cours appartient à " + course.getTeacher().getEmail() + " mais vous êtes connecté en tant que " + userEmail);
        }

        Chapter chapter = new Chapter(request.getTitle(), course);
        chapter.setDescription(request.getDescription());
        chapter.setOrderIndex(request.getOrderIndex());
        Chapter savedChapter = chapterRepository.save(chapter);

        return mapToResponse(savedChapter);
    }

    public List<ChapterResponse> getChaptersByCourseId(Long courseId) {
        // Optionnel : vérifier si le cours existe d'abord
        if (!courseRepository.existsById(courseId)) {
            throw new RuntimeException("Cours non trouvé");
        }

        return chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ChapterResponse getChapterById(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new RuntimeException("Chapitre non trouvé"));
        
        return mapToResponse(chapter);
    }

    public ChapterResponse updateChapter(Long id, ChapterRequest request, String userEmail, boolean isAdmin) {
        Chapter chapter = chapterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chapitre non trouvé"));

        Course course = chapter.getCourse();
        if (!isAdmin && !course.getTeacher().getEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("Non autorisé : ce chapitre appartient à " + course.getTeacher().getEmail() + " mais vous êtes connecté en tant que " + userEmail);
        }

        chapter.setTitle(request.getTitle());
        chapter.setDescription(request.getDescription());
        chapter.setOrderIndex(request.getOrderIndex());
        // Optionnel : permettre de changer de cours ? Généralement non pour un chapitre.
        
        Chapter updatedChapter = chapterRepository.save(chapter);
        return mapToResponse(updatedChapter);
    }

    public void deleteChapter(Long id, String userEmail, boolean isAdmin) {
        Chapter chapter = chapterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chapitre non trouvé"));

        Course course = chapter.getCourse();
        if (!isAdmin && !course.getTeacher().getEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("Non autorisé : ce chapitre appartient à " + course.getTeacher().getEmail() + " mais vous êtes connecté en tant que " + userEmail);
        }

        contentCleanupService.beforeDeletingChapter(chapter);
        chapterRepository.delete(chapter);
    }

    public ChapterResponse mapToResponse(Chapter chapter) {
        return new ChapterResponse(
                chapter.getId(),
                chapter.getTitle(),
                chapter.getCourse().getId(),
                chapter.getDescription(),
                chapter.getOrderIndex(),
                chapter.getLessons().stream().map(lessonService::mapToResponse).collect(Collectors.toList())
        );
    }
}
