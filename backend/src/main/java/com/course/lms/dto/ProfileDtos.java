package com.course.lms.dto;

import com.course.lms.dto.FeedbackDtos.TeacherProfile;

import java.time.LocalDateTime;
import java.util.List;

/** Profils utilisateurs : étudiant, formateur, administrateur, super administrateur. */
public final class ProfileDtos {

    private ProfileDtos() {}

    public record NamesRequest(String firstName, String lastName) {}

    public record StudentCourse(Long courseId, String title, Long teacherId, String teacherName, int progress,
                                boolean completed, LocalDateTime enrolledAt, LocalDateTime lastAccessedAt) {}

    public record FollowedTeacher(Long teacherId, String name, String avatarUrl, List<String> courses) {}

    /** limited = vue d'un formateur : seuls les cours qu'il donne à cet étudiant sont listés. */
    public record StudentPart(int coursesCount, long completedCount, int averageProgress, List<StudentCourse> courses,
                              List<FollowedTeacher> teachers, boolean limited) {}

    /** email : visible uniquement par l'intéressé et l'administration. */
    public record ProfileView(Long userId, String firstName, String lastName, String name, String role, String email,
                              String avatarUrl, LocalDateTime memberSince, boolean self, StudentPart student,
                              TeacherProfile teacher) {}
}
