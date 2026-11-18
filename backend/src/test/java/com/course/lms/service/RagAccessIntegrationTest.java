package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recherche dans tous les cours (RAG) : réservée à l'administration, et l'index couvre les cours de tous les enseignants. */
@SpringBootTest
@AutoConfigureMockMvc
class RagAccessIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired RagService ragService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    @Autowired LessonResourceRepository resources;

    @Test
    void aTeacherOrAStudentCannotUseTheRag() throws Exception {
        for (String role : List.of("TEACHER", "STUDENT")) {
            User u = newUser(role);
            mvc.perform(post("/api/ai/rag/ask").with(user(u.getEmail()).roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Que dit le cours sur JWT ?\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/ai/rag/reindex").with(user(u.getEmail()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void theAdminReachesTheRag() throws Exception {
        User admin = newUser("ADMIN");
        int code = mvc.perform(post("/api/ai/rag/ask").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Que dit le cours sur JWT ?\"}"))
                .andReturn().getResponse().getStatus();
        // Accès accordé ; en test le service IA n'est pas lancé : la réponse vient de lui (503), jamais un refus
        assertThat(code).isNotIn(401, 403);
    }

    @Test
    void theIndexCoversTheLessonsAndResourcesOfEveryTeacher() {
        Course a = courseWithLesson(newUser("TEACHER"), "RAG enseignant A", "jwt.pdf");
        Course b = courseWithLesson(newUser("TEACHER"), "RAG enseignant B", "angular.pdf");

        List<Map<String, Object>> all = ragService.collectLessons();
        Map<String, Object> lessonA = all.stream().filter(l -> a.getId().equals(l.get("course_id"))).findFirst().orElseThrow();
        assertThat(all).anyMatch(l -> b.getId().equals(l.get("course_id")));
        assertThat(lessonA.get("course_title")).isEqualTo("RAG enseignant A");
        assertThat(lessonA.get("description")).isEqualTo("Description de la leçon");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> res = (List<Map<String, String>>) lessonA.get("resources");
        assertThat(res).singleElement().satisfies(r -> {
            assertThat(r.get("type")).isEqualTo("PDF");
            assertThat(r.get("file_path")).isEqualTo("uploads/jwt.pdf");
        });
    }

    private Course courseWithLesson(User teacher, String title, String file) {
        Course c = new Course();
        c.setTitle(title);
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        c = courses.save(c);
        Chapter ch = new Chapter();
        ch.setTitle("Chapitre 1");
        ch.setOrderIndex(1);
        ch.setCourse(c);
        ch = chapters.save(ch);
        Lesson l = new Lesson();
        l.setTitle("Leçon 1");
        l.setDescription("Description de la leçon");
        l.setOrderIndex(1);
        l.setChapter(ch);
        l = lessons.save(l);
        LessonResource r = new LessonResource();
        r.setLesson(l);
        r.setResourceType(ResourceType.PDF);
        r.setTitle(file);
        r.setFilePath("uploads/" + file);
        resources.save(r);
        return c;
    }

    private User newUser(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("RAG " + role + " " + id);
        u.setEmail("rag-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }
}
