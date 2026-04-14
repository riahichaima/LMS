package com.course.lms.service;

import com.course.lms.dto.CourseRequest;
import com.course.lms.dto.CourseResponse;
import com.course.lms.model.Course;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    public CourseResponse createCourse(CourseRequest request, String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new RuntimeException("Professeur non trouvé"));

        Course course = new Course(request.getTitle(), request.getDescription(), teacher);
        Course savedCourse = courseRepository.save(course);

        return mapToResponse(savedCourse);
    }

    public Page<CourseResponse> getAllCourses(Pageable pageable) {
        return courseRepository.findAll(pageable).map(this::mapToResponse);
    }

    public Page<CourseResponse> searchCourses(String title, Pageable pageable) {
        return courseRepository.findByTitleContainingIgnoreCase(title, pageable)
                .map(this::mapToResponse);
    }

    public List<CourseResponse> getMyCourses(String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new RuntimeException("Professeur non trouvé"));

        return courseRepository.findByTeacherId(teacher.getId())
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));
        return mapToResponse(course);
    }

    public CourseResponse updateCourse(Long id, CourseRequest request, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        // Vérification des droits
        if (!isAdmin && !course.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à modifier ce cours");
        }

        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        Course updatedCourse = courseRepository.save(course);

        return mapToResponse(updatedCourse);
    }

    public void deleteCourse(Long id, String userEmail, boolean isAdmin) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (!isAdmin && !course.getTeacher().getEmail().equals(userEmail)) {
            throw new RuntimeException("Non autorisé à supprimer ce cours");
        }

        courseRepository.delete(course);
    }

    private CourseResponse mapToResponse(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getTeacher().getId(),
                course.getTeacher().getName()
        );
    }
}
