package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'administration crée des cours pour un enseignant, voit tous les cours et génère des quiz et TP avec l'IA
 * pour n'importe quel cours ; un enseignant reste limité à ses propres cours (vraie API, vraie base MySQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPedagogyIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired QuizRepository quizzes;
    @Autowired LessonRepository lessons;
    @Autowired SubmissionRepository submissions;
    @Autowired NotificationRepository notifications;

    // ---------------------------------------------------------------- cours affecté par l'administration

    private java.util.List<String> typesFor(User u) {
        return notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(u.getId()).stream().map(Notification::getType).toList();
    }

    @Test
    void aCourseCreatedByTheAdminIsMarkedAndTheTeacherIsNotified() throws Exception {
        User admin = newUser("ADMIN");
        User teacher = newUser("TEACHER");
        mvc.perform(post("/api/teacher/courses").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cours affecté\",\"teacherId\":" + teacher.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedByAdmin").value(true));
        assertThat(typesFor(teacher)).contains("COURSE_ASSIGNED");
    }

    @Test
    void aCourseCreatedByTheTeacherIsNotMarked() throws Exception {
        User teacher = newUser("TEACHER");
        mvc.perform(post("/api/teacher/courses").with(user(teacher.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Mon cours\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedByAdmin").value(false));
        assertThat(typesFor(teacher)).doesNotContain("COURSE_ASSIGNED");
    }

    @Test
    void assigningAnExistingCourseNotifiesTheNewTeacher() throws Exception {
        User admin = newUser("ADMIN");
        User newTeacher = newUser("TEACHER");
        Course course = courseOf(newUser("TEACHER"));
        mvc.perform(put("/api/admin/courses/" + course.getId() + "/assign-teacher/" + newTeacher.getId())
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedByAdmin").value(true));
        assertThat(typesFor(newTeacher)).contains("COURSE_ASSIGNED");
    }

    // ---------------------------------------------------------------- analyse de progression d'un cours

    @Test
    void theCourseProgressAnalysisUsesOnlyTheCopiesOfThatCourseAndIsForTheAdmin() throws Exception {
        User admin = newUser("ADMIN");
        User teacher = newUser("TEACHER");
        Quiz q1 = quizOf(chapterOf(courseOf(teacher)));
        Quiz other = quizOf(chapterOf(courseOf(teacher)));
        for (User s : new User[]{newUser("STUDENT"), newUser("STUDENT")}) copy(s, q1, 6.0, 10.0);
        copy(newUser("STUDENT"), other, 1.0, 10.0);
        Long courseId = q1.getChapter().getCourse().getId();

        assertThat(submissions.findByCourseId(courseId)).hasSize(2);
        mvc.perform(get("/api/progress/analyze/course/" + courseId).with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
        var async = mvc.perform(get("/api/progress/analyze/course/" + courseId).with(user(admin.getEmail()).roles("ADMIN")))
                .andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionCount").value(2))
                .andExpect(jsonPath("$.studentCount").value(2))
                .andExpect(jsonPath("$.averageScore").value(60.0));
    }

    private void copy(User student, Quiz quiz, double points, double max) {
        Submission s = new Submission();
        s.setStudent(student);
        s.setQuiz(quiz);
        s.setSubmittedAt(java.time.LocalDateTime.now());
        s.setPoints(points);
        s.setMaxPoints(max);
        submissions.save(s);
    }

    // ---------------------------------------------------------------- création de cours

    @Test
    void anAdminMustChooseTheTeacherOfANewCourse() throws Exception {
        User admin = newUser("ADMIN");
        mvc.perform(post("/api/teacher/courses").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Cours sans formateur\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choisissez le formateur du cours."));
    }

    @Test
    void aCourseCreatedByAnAdminBelongsToTheChosenTeacher() throws Exception {
        User admin = newUser("ADMIN");
        User teacher = newUser("TEACHER");
        mvc.perform(post("/api/teacher/courses").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cours de l'administration\",\"teacherId\":" + teacher.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(teacher.getId()));
        assertThat(courses.findByTeacherId(teacher.getId())).hasSize(1);
    }

    @Test
    void theChosenTeacherMustReallyBeATeacher() throws Exception {
        User admin = newUser("ADMIN");
        User student = newUser("STUDENT");
        mvc.perform(post("/api/teacher/courses").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cours\",\"teacherId\":" + student.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTeacherCannotCreateACourseInAnotherTeachersName() throws Exception {
        User teacher = newUser("TEACHER");
        User other = newUser("TEACHER");
        mvc.perform(post("/api/teacher/courses").with(user(teacher.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mon cours\",\"teacherId\":" + other.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(teacher.getId()));
        assertThat(courses.findByTeacherId(other.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- liste des cours

    @Test
    void theAdminSeesTheCoursesOfEveryTeacherAndATeacherOnlyHisOwn() throws Exception {
        User admin = newUser("ADMIN");
        User teacher = newUser("TEACHER");
        User other = newUser("TEACHER");
        Course course = courseOf(teacher);

        String adminJson = mvc.perform(get("/api/teacher/courses").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<java.util.List<Object>>read(adminJson, "$[?(@.id == " + course.getId() + ")]")).hasSize(1);

        mvc.perform(get("/api/teacher/courses").with(user(other.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------- génération par l'IA

    @Test
    void aTeacherCannotGenerateForAnotherTeachersCourse() throws Exception {
        User teacher = newUser("TEACHER");
        User other = newUser("TEACHER");
        Chapter chapter = chapterOf(courseOf(teacher));
        mvc.perform(post("/api/assessments/generate").with(user(other.getEmail()).roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(generation(chapter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminCanGenerateForAnyTeachersCourse() throws Exception {
        User admin = newUser("ADMIN");
        User teacher = newUser("TEACHER");
        Chapter chapter = chapterOf(courseOf(teacher));
        int code = mvc.perform(post("/api/assessments/generate").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(generation(chapter)))
                .andReturn().getResponse().getStatus();
        // Le contrôle d'accès est passé ; en test, le service IA n'est pas lancé, l'erreur éventuelle vient de lui
        assertThat(code).isNotIn(401, 403);
    }

    @Test
    void theAdminCanDeleteAnAiQuizOfAnyCourseButAnotherTeacherCannot() throws Exception {
        User admin = newUser("ADMIN");
        User teacher = newUser("TEACHER");
        User other = newUser("TEACHER");
        Quiz quiz = quizOf(chapterOf(courseOf(teacher)));

        mvc.perform(delete("/api/assessments/manual/quizzes/" + quiz.getId()).with(user(other.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/assessments/manual/quizzes/" + quiz.getId()).with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isNoContent());
        assertThat(quizzes.findById(quiz.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- données de test

    private static String generation(Chapter chapter) {
        return "{\"chapterIds\":[" + chapter.getId() + "],\"generateQuiz\":true,\"generateAssignment\":false,"
                + "\"quizQuestionCount\":5,\"difficultyLevels\":[\"BEGINNER\"]}";
    }

    private User newUser(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Pédagogie " + role + " " + id);
        u.setEmail("peda-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private Course courseOf(User teacher) {
        Course c = new Course();
        c.setTitle("Cours " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        return courses.save(c);
    }

    private Chapter chapterOf(Course course) {
        Chapter ch = new Chapter();
        ch.setTitle("Chapitre 1");
        ch.setOrderIndex(1);
        ch.setCourse(course);
        return chapters.save(ch);
    }

    private Quiz quizOf(Chapter chapter) {
        Lesson lesson = new Lesson();
        lesson.setTitle("Leçon 1");
        lesson.setOrderIndex(1);
        lesson.setChapter(chapter);
        lesson = lessons.save(lesson);
        Quiz q = new Quiz();
        q.setTitle("Quiz IA");
        q.setChapter(chapter);
        q.setLesson(lesson);
        q.setDifficultyLevel(DifficultyLevel.BEGINNER);
        q.setQuestionCount(5);
        q.setTimeLimitMinutes(10);
        q.setPassingScore(50.0);
        q.setGeneratedByAi(true);
        return quizzes.save(q);
    }
}
