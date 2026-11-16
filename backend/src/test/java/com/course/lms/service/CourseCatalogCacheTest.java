package com.course.lms.service;

import com.course.lms.dto.ChapterResponse;
import com.course.lms.dto.CourseResponse;
import com.course.lms.dto.LessonResourceResponse;
import com.course.lms.dto.LessonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.course.lms.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cache du catalogue : une seule lecture en base, copies indépendantes, vidage, mêmes erreurs qu'avant. */
class CourseCatalogCacheTest {

    private final AtomicInteger dbReads = new AtomicInteger();

    private static CourseResponse sampleCourse(long id) {
        LessonResourceResponse pdf = new LessonResourceResponse();
        pdf.setId(7L);
        pdf.setResourceType(com.course.lms.model.ResourceType.PDF);
        pdf.setTitle("Support du chapitre 1");
        pdf.setFilePath("uploads/3f2a.pdf");
        pdf.setMimeType("application/pdf");
        pdf.setSize(120_000L);
        LessonResponse lesson = new LessonResponse();
        lesson.setId(5L);
        lesson.setTitle("Introduction");
        lesson.setChapterId(3L);
        lesson.setCourseName("Java");
        lesson.setChapterTitle("Bases");
        lesson.setOrderIndex(1);
        lesson.setResources(new ArrayList<>(List.of(pdf)));
        ChapterResponse chapter = new ChapterResponse(3L, "Bases", id, "Les bases", 1, new ArrayList<>(List.of(lesson)));
        CourseResponse course = new CourseResponse();
        course.setId(id);
        course.setTitle("Java");
        course.setDescription("Cours complet");
        course.setTeacherId(2L);
        course.setTeacherName("Mme Ben Ali");
        course.setStatus("PUBLISHED");
        course.setThumbnail("uploads/java.png");
        course.setPricingType("PAID");
        course.setPrice(new BigDecimal("19.90"));
        course.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 30));
        course.setUpdatedAt(LocalDateTime.of(2026, 11, 2, 18, 5));
        course.setChapters(new ArrayList<>(List.of(chapter)));
        return course;
    }

    private CourseResponse fromDatabase(long id) {
        dbReads.incrementAndGet();
        return sampleCourse(id);
    }

    private static CourseCatalogCache cache() {
        return new CourseCatalogCache(new ConcurrentMapCacheManager(CourseCatalogCache.NAME));
    }

    @Test
    void secondReadOfTheSameCourseDoesNotQueryTheDatabase() {
        CourseCatalogCache cache = cache();
        cache.course(1L, () -> fromDatabase(1L));
        cache.course(1L, () -> fromDatabase(1L));
        cache.course(1L, () -> fromDatabase(1L));
        assertThat(dbReads).hasValue(1);
    }

    @Test
    void copyKeepsEveryField() throws Exception {
        CourseCatalogCache cache = cache();
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        CourseResponse original = sampleCourse(1L);
        assertThat(json.writeValueAsString(cache.copy(original))).isEqualTo(json.writeValueAsString(original));
    }

    @Test
    void hidingResourcesForOneStudentDoesNotChangeTheCachedCourse() {
        CourseCatalogCache cache = cache();
        CourseResponse forStudent = cache.course(1L, () -> fromDatabase(1L));
        // Même opération que CourseAccessService.protect pour un étudiant qui n'a pas acheté
        forStudent.getChapters().forEach(ch -> ch.getLessons().forEach(l -> l.setResources(new ArrayList<>())));

        CourseResponse forBuyer = cache.course(1L, () -> fromDatabase(1L));
        assertThat(forBuyer.getChapters().get(0).getLessons().get(0).getResources()).hasSize(1);
        assertThat(dbReads).hasValue(1);
    }

    @Test
    void clearForcesAFreshRead() {
        CourseCatalogCache cache = cache();
        cache.course(1L, () -> fromDatabase(1L));
        cache.clear();
        cache.course(1L, () -> fromDatabase(1L));
        assertThat(dbReads).hasValue(2);
    }

    @Test
    void unknownCourseKeepsTheSameErrorAndIsNotCached() {
        CourseCatalogCache cache = cache();
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cache.course(99L, () -> {
                dbReads.incrementAndGet();
                throw new RuntimeException("Cours non trouvé");
            })).isExactlyInstanceOf(RuntimeException.class).hasMessage("Cours non trouvé");
        }
        assertThat(dbReads).hasValue(2);
    }

    private java.util.function.Supplier<Page<CourseResponse>> pageLoader(PageRequest request) {
        return () -> {
            dbReads.incrementAndGet();
            return new PageImpl<>(List.of(sampleCourse(1L), sampleCourse(2L)), request, 2);
        };
    }

    @Test
    void cataloguePagesAreCachedPerPage() {
        CourseCatalogCache cache = cache();
        PageRequest first = PageRequest.of(0, 20);
        Page<CourseResponse> a = cache.page(first, pageLoader(first));
        Page<CourseResponse> b = cache.page(first, pageLoader(first));
        cache.page(PageRequest.of(1, 20), pageLoader(PageRequest.of(1, 20)));

        assertThat(dbReads).hasValue(2); // page 1 (une seule fois) et page 2
        assertThat(b.getContent()).hasSize(2);
        assertThat(b.getTotalElements()).isEqualTo(2);
        assertThat(a.getContent().get(0)).isNotSameAs(b.getContent().get(0));
    }

    @Test
    void theSitesCataloguePageOf200CoursesIsCached() {
        CourseCatalogCache cache = cache();
        PageRequest site = PageRequest.of(0, 200); // requête du site : /api/courses?size=200
        cache.page(site, pageLoader(site));
        cache.page(site, pageLoader(site));
        assertThat(dbReads).hasValue(1);
    }

    @Test
    void sortedHugeOrFarPagesAreNeverKeptInMemory() {
        CourseCatalogCache cache = cache();
        PageRequest sorted = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("title", "title", "price"));
        PageRequest huge = PageRequest.of(0, 2000);
        PageRequest far = PageRequest.of(CourseCatalogCache.MAX_CACHED_PAGE_NUMBER, 20);
        for (int i = 0; i < 2; i++) {
            cache.page(sorted, pageLoader(sorted)); // tri choisi par le client (clés longues et variées)
            cache.page(huge, pageLoader(huge));     // page géante
            cache.page(far, pageLoader(far));       // page lointaine
        }
        assertThat(dbReads).hasValue(6); // tout est relu en base : rien n'a été gardé
    }

    @Test
    void weightCountsCoursesChaptersLessonsAndResources() {
        assertThat(CourseCatalogCache.weight(sampleCourse(1L))).isEqualTo(4); // 1 cours, 1 chapitre, 1 leçon, 1 ressource
    }

    @Test
    void memoryIsBoundedByWeightWhateverTheRequests() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CourseCatalogCache.NAME);
        manager.setCaffeine(CacheConfig.builder(1_000, java.time.Duration.ofMinutes(10)));
        CourseCatalogCache cache = new CourseCatalogCache(manager);
        for (long id = 1; id <= 2_000; id++) {
            final long n = id;
            cache.course(n, () -> sampleCourse(n)); // 2 000 cours de poids 4 = 8 000
        }
        var store = ((CaffeineCache) manager.getCache(CourseCatalogCache.NAME)).getNativeCache();
        store.cleanUp();
        assertThat(store.policy().eviction().orElseThrow().weightedSize().orElseThrow()).isLessThanOrEqualTo(1_000);
    }

    @Test
    void cacheTypeNoneMeansEveryReadGoesToTheDatabase() {
        CourseCatalogCache cache = new CourseCatalogCache(new NoOpCacheManager());
        cache.course(1L, () -> fromDatabase(1L));
        cache.course(1L, () -> fromDatabase(1L));
        assertThat(dbReads).hasValue(2);
    }
}
