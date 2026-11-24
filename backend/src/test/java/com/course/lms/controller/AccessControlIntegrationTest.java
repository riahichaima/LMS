package com.course.lms.controller;

import com.course.lms.model.Course;
import com.course.lms.model.Enrollment;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôle d'accès sur un vrai serveur HTTP : un enseignant ne lit ni le contenu payant ni les réponses d'un autre
 * enseignant ; un étudiant ne lit ni ne modifie les données d'un autre étudiant. Chaque cas est vérifié dans les
 * deux sens : refusé pour l'autre (403), accepté pour le propriétaire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessControlIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserDetailsService userDetails;

    private final HttpClient http = HttpClient.newHttpClient();
    private User teacherA, teacherB, studentA, studentB;
    private long course, chapter, lesson, quiz, assignment, quizCopy, tpCopy, enrollment;

    @BeforeEach
    void data() {
        teacherA = user("TEACHER");
        teacherB = user("TEACHER");
        studentA = user("STUDENT");
        studentB = user("STUDENT");
        Course c = new Course();
        c.setTitle("Cours payant de A");
        c.setStatus("PUBLISHED");
        c.setTeacher(teacherA);
        c.setPricingType("PAID");
        c.setPrice(new BigDecimal("49.00"));
        course = courses.save(c).getId();
        chapter = insert("INSERT INTO chapters (title, course_id, created_at) VALUES ('Chapitre', ?, NOW())", course);
        lesson = insert("INSERT INTO lessons (title, chapter_id, created_at) VALUES ('Leçon', ?, NOW())", chapter);
        insert("INSERT INTO lesson_resources (title, resource_type, file_path, lesson_id, created_at) "
                + "VALUES ('Support', 'PDF', '/app/uploads/support-payant.pdf', ?, NOW())", lesson);
        quiz = insert("INSERT INTO quizzes (title, difficulty_level, generated_by_ai, passing_score, question_count, "
                + "time_limit_minutes, lesson_id, chapter_id, created_at) VALUES ('Quiz', 'BEGINNER', 1, 50, 1, 10, ?, ?, NOW())", lesson, chapter);
        insert("INSERT INTO questions (quiz_id, question_text, question_type, correct_answer, points, question_order) "
                + "VALUES (?, 'Capitale ?', 'MULTIPLE_CHOICE', 'Tunis', 1, 1)", quiz);
        assignment = insert("INSERT INTO assignments (title, difficulty_level, exercise_count, generated_by_ai, max_points, "
                + "lesson_id, chapter_id, created_at, description, expected_solution) "
                + "VALUES ('TP', 'BEGINNER', 1, 1, 20, ?, ?, NOW(), 'Énoncé', 'Solution')", lesson, chapter);
        quizCopy = insert("INSERT INTO submissions (student_id, quiz_id, content, points, max_points, submitted_at) "
                + "VALUES (?, ?, '{\"1\":\"Tunis\"}', 1, 1, NOW())", studentA.getId(), quiz);
        tpCopy = insert("INSERT INTO submissions (student_id, assignment_id, content, points, max_points, submitted_at) "
                + "VALUES (?, ?, 'Copie de A', 15, 20, NOW())", studentA.getId(), assignment);
        Enrollment e = new Enrollment(studentA, courses.findById(course).orElseThrow());
        enrollment = enrollments.save(e).getId();
    }

    @Test
    void aTeacherCannotReadAnotherTeachersPaidCourse() throws Exception {
        assertThat(get(teacherB, "/api/teacher/courses/" + course)).isEqualTo(403);
        assertThat(get(teacherB, "/api/teacher/chapters/" + chapter)).isEqualTo(403);
        assertThat(get(teacherB, "/api/teacher/lessons/" + lesson)).isEqualTo(403);
        assertThat(get(teacherA, "/api/teacher/courses/" + course)).isEqualTo(200);
        assertThat(get(teacherA, "/api/teacher/lessons/" + lesson)).isEqualTo(200);
    }

    @Test
    void aTeacherCannotReadAnotherTeachersAnswers() throws Exception {
        assertThat(get(teacherB, "/api/assessments/quizzes/" + quiz + "/detail")).isEqualTo(403);
        assertThat(get(teacherB, "/api/assessments/chapter/" + chapter + "/review")).isEqualTo(403);
        assertThat(get(teacherA, "/api/assessments/quizzes/" + quiz + "/detail")).isEqualTo(200);
        assertThat(get(teacherA, "/api/assessments/chapter/" + chapter + "/review")).isEqualTo(200);
    }

    @Test
    void aStudentCannotReadAnotherStudentsCorrection() throws Exception {
        String url = "/api/assessments/quizzes/" + quiz + "/correction/" + quizCopy;
        assertThat(get(studentB, url)).isEqualTo(403);
        assertThat(get(teacherB, url)).isEqualTo(403);
        assertThat(get(studentA, url)).isEqualTo(200);
        assertThat(get(teacherA, url)).isEqualTo(200);
    }

    @Test
    void aStudentCannotRegradeAnotherStudentsCopy() throws Exception {
        String body = "{\"submissionId\":" + tpCopy + ",\"assignmentId\":" + assignment + ",\"studentAnswer\":\"x\"}";
        assertThat(send(studentB, "POST", "/api/assessments/submissions/correct", body)).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT points FROM submissions WHERE id = ?", Double.class, tpCopy)).isEqualTo(15.0);
    }

    @Test
    void aStudentCannotChangeAnotherStudentsProgress() throws Exception {
        assertThat(send(studentB, "PUT", "/api/student/enrollments/" + enrollment + "/progress?progressPercentage=77", "")).isEqualTo(403);
        assertThat(send(studentB, "PUT", "/api/student/enrollments/" + enrollment + "/complete", "")).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT progress_percentage FROM enrollments WHERE id = ?", Integer.class, enrollment))
                .isNotEqualTo(77).isNotEqualTo(100);
        assertThat(send(studentA, "PUT", "/api/student/enrollments/" + enrollment + "/progress?progressPercentage=40", "")).isEqualTo(200);
    }

    @Test
    void progressAnalysisIsLimitedToOneselfOrTheCourseTeacher() throws Exception {
        String url = "/api/progress/analyze/" + studentA.getId() + "/course/" + course;
        assertThat(get(studentB, url)).isEqualTo(403);
        assertThat(get(teacherB, url)).isEqualTo(403);
    }

    @Test
    void noRoleCanBeInventedAtRegistration() throws Exception {
        String body = "{\"name\":\"Test\",\"email\":\"role-" + UUID.randomUUID() + "@test.lms\","
                + "\"password\":\"Passw0rd!\",\"role\":\"HACKER\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/register"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        assertThat(roles.findByName("HACKER")).isEmpty();
    }

    // ── Outils ──
    private User user(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Accès " + role + " " + id);
        u.setEmail("acces-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private String token(User u) {
        return jwtUtil.generateToken(userDetails.loadUserByUsername(u.getEmail()));
    }

    private int get(User u, String path) throws Exception {
        return send(u, "GET", path, null);
    }

    private int send(User u, String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token(u)).header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
