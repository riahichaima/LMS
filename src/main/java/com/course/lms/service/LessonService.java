package com.course.lms.service;

import com.course.lms.dto.LessonRequest;
import com.course.lms.dto.LessonResponse;
import com.course.lms.model.Chapter;
import com.course.lms.model.Course;
import com.course.lms.model.FileStorage;
import com.course.lms.model.Lesson;
import com.course.lms.repository.ChapterRepository;
import com.course.lms.repository.FileStorageRepository;
import com.course.lms.repository.LessonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LessonService {

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private FileStorageRepository fileStorageRepository;

    public LessonResponse createLesson(LessonRequest request, String userEmail, boolean isAdmin) {
        Chapter chapter = chapterRepository.findById(request.getChapterId())
                .orElseThrow(() -> new RuntimeException("Chapitre non trouvé"));

        Course parentCourse = chapter.getCourse();
        if (!isAdmin && !parentCourse.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à ajouter une leçon à ce chapitre");
        }

        // Déterminer l'URL du contenu
        String contentUrl = request.getContentUrl();
        FileStorage fileStorage = null;

        // Si c'est un type qui nécessite un fichier uploadé (PDF ou VIDEO)
        if (request.getFileId() != null) {
            fileStorage = fileStorageRepository.findById(request.getFileId())
                    .orElseThrow(() -> new RuntimeException("Fichier non trouvé"));
            contentUrl = fileStorage.getDownloadUrl();
        }
        // Si c'est un type LINK, on utilise l'URL directe
        else if (request.getContentType() == com.course.lms.model.LessonContentType.LINK) {
            contentUrl = request.getContentUrl();
        }
        // Si c'est un type VIDEO avec lien YouTube
        else if (request.getContentType() == com.course.lms.model.LessonContentType.VIDEO && request.getContentUrl() != null) {
            contentUrl = request.getContentUrl();
        }

        Lesson lesson = new Lesson(
                request.getTitle(),
                request.getContentType(),
                contentUrl,
                request.getDescription(),
                chapter
        );

        Lesson savedLesson = lessonRepository.save(lesson);

        return mapToResponse(savedLesson, fileStorage);
    }

    public List<LessonResponse> getLessonsByChapterId(Long chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new RuntimeException("Chapitre non trouvé");
        }

        return lessonRepository.findByChapterId(chapterId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public LessonResponse getLessonById(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Leçon non trouvée"));
        
        return mapToResponse(lesson);
    }

    private LessonResponse mapToResponse(Lesson lesson) {
        return mapToResponse(lesson, null);
    }

    private LessonResponse mapToResponse(Lesson lesson, FileStorage fileStorage) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getContentType(),
                lesson.getContentUrl(),
                lesson.getDescription(),
                lesson.getChapter().getId(),
                lesson.getChapter().getCourse().getTitle(),  // <- Nom du cours
                lesson.getChapter().getTitle(),               // <- Nom du chapitre
                fileStorage != null ? fileStorage.getId() : null,
                fileStorage != null ? fileStorage.getOriginalFileName() : null,
                fileStorage != null ? fileStorage.getStoredFileName() : null,
                fileStorage != null ? fileStorage.getDownloadUrl() : null,
                fileStorage != null ? fileStorage.getFileSize() : null,
                fileStorage != null ? fileStorage.getContentType() : null
        );
    }
}
