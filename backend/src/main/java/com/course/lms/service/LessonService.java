package com.course.lms.service;

import com.course.lms.dto.LessonResourceResponse;
import com.course.lms.dto.LessonRequest;
import com.course.lms.dto.LessonResponse;
import com.course.lms.model.*;
import com.course.lms.repository.ChapterRepository;
import com.course.lms.repository.LessonResourceRepository;
import com.course.lms.repository.FileStorageRepository;
import com.course.lms.repository.LessonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LessonService {

    @Autowired
    private ContentCleanupService contentCleanupService;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private LessonResourceRepository lessonResourceRepository;

    @Autowired
    private FileStorageRepository fileStorageRepository;

    @Transactional
    public LessonResponse createLesson(LessonRequest request, String userEmail, boolean isAdmin) {
        Chapter chapter = chapterRepository.findById(request.getChapterId())
                .orElseThrow(() -> new RuntimeException("Chapitre non trouvé"));

        Course parentCourse = chapter.getCourse();
        if (!isAdmin && !parentCourse.getTeacher().getEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("Non autorisé : ce cours appartient à " + parentCourse.getTeacher().getEmail() + " mais vous êtes connecté en tant que " + userEmail);
        }

        Lesson lesson = new Lesson();
        lesson.setTitle(request.getTitle());
        lesson.setDescription(request.getDescription());
        lesson.setOrderIndex(request.getOrderIndex());
        lesson.setChapter(chapter);
        Lesson savedLesson = lessonRepository.save(lesson);

        List<LessonResource> resources = new ArrayList<>();
        if (request.getResources() != null) {
            request.getResources().forEach(rr -> {
                LessonResource resource = new LessonResource();
                resource.setLesson(savedLesson);
                resource.setResourceType(rr.getResourceType());
                resource.setTitle(rr.getTitle());
                resource.setFilePath(rr.getFilePath());
                resource.setExternalUrl(rr.getExternalUrl());
                resource.setMimeType(rr.getMimeType());
                resource.setSize(rr.getSize());
                resources.add(lessonResourceRepository.save(resource));
            });
        }
        savedLesson.setResources(resources);

        return mapToResponse(savedLesson);
    }

    @Transactional
    public LessonResponse updateLesson(Long id, LessonRequest request, String userEmail, boolean isAdmin) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leçon non trouvée"));

        Course parentCourse = lesson.getChapter().getCourse();
        if (!isAdmin && !parentCourse.getTeacher().getEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("Non autorisé : cette leçon appartient à " + parentCourse.getTeacher().getEmail() + " mais vous êtes connecté en tant que " + userEmail);
        }

        lesson.setTitle(request.getTitle());
        lesson.setDescription(request.getDescription());
        lesson.setOrderIndex(request.getOrderIndex());

        // Update chapter if changed
        if (request.getChapterId() != null && !lesson.getChapter().getId().equals(request.getChapterId())) {
            Chapter newChapter = chapterRepository.findById(request.getChapterId())
                    .orElseThrow(() -> new RuntimeException("Nouveau chapitre non trouvé"));
            lesson.setChapter(newChapter);
        }

        // Handle resources update (simple approach: clear and re-add)
        lesson.getResources().clear();
        lessonRepository.saveAndFlush(lesson); // Flush the clear to delete orphans

        if (request.getResources() != null) {
            request.getResources().forEach(rr -> {
                LessonResource resource = new LessonResource();
                resource.setLesson(lesson);
                resource.setResourceType(rr.getResourceType());
                resource.setTitle(rr.getTitle());
                resource.setFilePath(rr.getFilePath());
                resource.setExternalUrl(rr.getExternalUrl());
                resource.setMimeType(rr.getMimeType());
                resource.setSize(rr.getSize());
                lesson.getResources().add(lessonResourceRepository.save(resource));
            });
        }

        Lesson updatedLesson = lessonRepository.save(lesson);
        return mapToResponse(updatedLesson);
    }

    @Transactional
    public void deleteLesson(Long id, String userEmail, boolean isAdmin) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leçon non trouvée"));

        Course parentCourse = lesson.getChapter().getCourse();
        if (!isAdmin && !parentCourse.getTeacher().getEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("Non autorisé : cette leçon appartient à " + parentCourse.getTeacher().getEmail() + " mais vous êtes connecté en tant que " + userEmail);
        }

        contentCleanupService.beforeDeletingLessons(java.util.List.of(lesson));
        lessonRepository.delete(lesson);
    }

    public List<LessonResponse> getLessonsByChapterId(Long chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new RuntimeException("Chapitre non trouvé");
        }

        return lessonRepository.findByChapterIdOrderByOrderIndexAsc(chapterId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public LessonResponse getLessonById(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Leçon non trouvée"));
        
        return mapToResponse(lesson);
    }

    public LessonResponse mapToResponse(Lesson lesson) {
        List<LessonResourceResponse> resourceResponses = lesson.getResources().stream()
                .map(res -> new LessonResourceResponse(
                        res.getId(),
                        res.getResourceType(),
                        res.getTitle(),
                        res.getFilePath(),
                        res.getExternalUrl(),
                        res.getMimeType(),
                        res.getSize()
                ))
                .collect(Collectors.toList());

        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getChapter().getId(),
                lesson.getChapter().getCourse().getTitle(),
                lesson.getChapter().getTitle(),
                lesson.getOrderIndex(),
                resourceResponses
        );
    }
}
