package com.course.lms.service;

import com.course.lms.model.*;
import com.course.lms.repository.*;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cache du catalogue sur la vraie API et une vraie base MySQL : jamais de données périmées ni mélangées. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogueCacheIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CacheManager cacheManager;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    @Autowired LessonResourceRepository resources;

    private CacheStats stats() {
        return ((CaffeineCache) cacheManager.getCache(CourseCatalogCache.NAME)).getNativeCache().stats();
    }

    private static MockHttpServletRequestBuilder as(User u, String role, String url) {
        return get(url).with(user(u.getEmail()).roles(role));
    }

    @Test
    void secondReadIsServedFromTheCache() throws Exception {
        User teacher = newUser("TEACHER");
        Course course = paidCourseWithOnePdf(teacher);
        String url = "/api/courses/" + course.getId();

        long hitsBefore = stats().hitCount();
        mvc.perform(as(teacher, "TEACHER", url)).andExpect(status().isOk());
        mvc.perform(as(teacher, "TEACHER", url)).andExpect(status().isOk());
        assertThat(stats().hitCount() - hitsBefore).isEqualTo(1);
    }

    @Test
    void studentWhoDidNotBuySeesNoFileButTheTeacherStillDoes() throws Exception {
        User teacher = newUser("TEACHER");
        User student = newUser("STUDENT");
        Course course = paidCourseWithOnePdf(teacher);
        String url = "/api/courses/" + course.getId();
        String files = "$.chapters[0].lessons[0].resources.length()";

        mvc.perform(as(student, "STUDENT", url)).andExpect(jsonPath(files).value(0)); // masqué
        mvc.perform(as(teacher, "TEACHER", url)).andExpect(jsonPath(files).value(1)); // toujours visible
        mvc.perform(as(student, "STUDENT", url)).andExpect(jsonPath(files).value(0));
    }

    @Test
    void newChapterAndNewTeacherNameAppearImmediately() throws Exception {
        User teacher = newUser("TEACHER");
        Course course = paidCourseWithOnePdf(teacher);
        String url = "/api/courses/" + course.getId();
        mvc.perform(as(teacher, "TEACHER", url)).andExpect(jsonPath("$.chapters.length()").value(1));

        Chapter second = new Chapter();
        second.setTitle("Chapitre 2");
        second.setOrderIndex(2);
        second.setCourse(course);
        chapters.save(second);
        mvc.perform(as(teacher, "TEACHER", url)).andExpect(jsonPath("$.chapters.length()").value(2));

        teacher.setName("Nouveau nom");
        users.save(teacher);
        mvc.perform(as(teacher, "TEACHER", url)).andExpect(jsonPath("$.teacherName").value("Nouveau nom"));
    }

    private long catalogueTotal(User u) throws Exception {
        String json = mvc.perform(as(u, "TEACHER", "/api/courses?size=200")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<Number>read(json, "$.totalElements").longValue();
    }

    @Test
    void catalogueShowsANewCourseImmediately() throws Exception {
        User teacher = newUser("TEACHER");
        long before = catalogueTotal(teacher); // page du site, gardée en cache

        Course c = new Course();
        c.setTitle("Catalogue " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        courses.save(c);
        assertThat(catalogueTotal(teacher)).isEqualTo(before + 1);
    }

    @Test
    void aSearchMadeOfSpacesNeverEmptiesTheCatalogue() throws Exception {
        User teacher = newUser("TEACHER");
        paidCourseWithOnePdf(teacher);
        mvc.perform(as(teacher, "TEACHER", "/api/courses?size=200&title=%20%20%20")).andExpect(status().isOk());
        assertThat(catalogueTotal(teacher)).isPositive(); // la recherche « vide » n'a rien remplacé dans le cache
    }

    private User newUser(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Cache " + role + " " + id);
        u.setEmail("cache-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private Course paidCourseWithOnePdf(User teacher) {
        Course c = new Course();
        c.setTitle("Cours payant (cache)");
        c.setStatus("PUBLISHED");
        c.setTeacher(teacher);
        c.setPricingType("PAID");
        c.setPrice(new BigDecimal("19.90"));
        c = courses.save(c);
        Chapter ch = new Chapter();
        ch.setTitle("Chapitre 1");
        ch.setOrderIndex(1);
        ch.setCourse(c);
        ch = chapters.save(ch);
        Lesson l = new Lesson();
        l.setTitle("Leçon 1");
        l.setOrderIndex(1);
        l.setChapter(ch);
        l = lessons.save(l);
        LessonResource r = new LessonResource();
        r.setLesson(l);
        r.setResourceType(ResourceType.PDF);
        r.setTitle("Support");
        r.setFilePath("test-" + UUID.randomUUID() + ".pdf");
        resources.save(r);
        return c;
    }
}
