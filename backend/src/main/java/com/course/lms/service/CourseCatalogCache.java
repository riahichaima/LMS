package com.course.lms.service;

import com.course.lms.dto.CourseResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Supplier;

/**
 * Cache en mémoire (Caffeine) du catalogue et du détail des cours : la même page n'interroge
 * plus MySQL à chaque ouverture.
 * <ul>
 *   <li>Chaque lecture renvoie une <b>copie</b> : le masquage des ressources pour un étudiant
 *       qui n'a pas acheté le cours (CourseAccessService.protect) ne touche jamais la valeur gardée.</li>
 *   <li>Le cache est <b>vidé</b> dès qu'un cours, un chapitre, une leçon, une ressource ou un
 *       utilisateur change ({@link CatalogueChangeListener}), puis une seconde fois après la validation
 *       de la transaction ; une durée de vie maximale (10 min) sert de filet de sécurité.</li>
 *   <li>{@code CACHE_TYPE=none} désactive le cache sans toucher au code (mesure avant / après).</li>
 * </ul>
 */
@Component
public class CourseCatalogCache {

    public static final String NAME = "catalogue";

    /** Utilisé par l'écouteur JPA, que Hibernate crée lui-même (hors de Spring). */
    private static volatile CourseCatalogCache active;

    private final Cache cache;
    private final ObjectMapper copier = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public CourseCatalogCache(CacheManager cacheManager) {
        this.cache = cacheManager.getCache(NAME);
        active = this;
    }

    /** Pages gardées : celles du catalogue normal (le site demande 200 cours par page), sans tri. */
    static final int MAX_CACHED_PAGE_SIZE = 200;
    static final int MAX_CACHED_PAGE_NUMBER = 20;

    /**
     * Page du catalogue (sans recherche : les recherches ne passent jamais par ici). Seules les pages non
     * triées, de taille et de numéro raisonnables, sont gardées : les clés restent courtes et en nombre
     * limité, et la place occupée est en plus bornée en poids (CacheConfig).
     */
    public Page<CourseResponse> page(Pageable pageable, Supplier<Page<CourseResponse>> loader) {
        boolean cacheable = pageable.isPaged() && pageable.getSort().isUnsorted()
                && pageable.getPageSize() <= MAX_CACHED_PAGE_SIZE && pageable.getPageNumber() < MAX_CACHED_PAGE_NUMBER;
        if (!cacheable) return loader.get();
        String key = "page|" + pageable.getPageNumber() + "|" + pageable.getPageSize();
        CachedPage cached = load(key, () -> {
            Page<CourseResponse> page = loader.get();
            return new CachedPage(List.copyOf(page.getContent()), page.getTotalElements());
        });
        return new PageImpl<>(cached.content().stream().map(this::copy).toList(), pageable, cached.total());
    }

    /** Détail d'un cours. */
    public CourseResponse course(Long id, Supplier<CourseResponse> loader) {
        return copy(load("course|" + id, loader));
    }

    /** Vide tout le cache maintenant et, s'il y a une transaction en cours, encore après sa validation. */
    public void clear() {
        if (cache == null) return;
        cache.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.clear();
                }
            });
        }
    }

    static void clearActive() {
        CourseCatalogCache current = active;
        if (current != null) current.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T load(String key, Supplier<T> loader) {
        if (cache == null) return loader.get();
        try {
            return (T) cache.get(key, loader::get);
        } catch (Cache.ValueRetrievalException e) {
            // Même erreur qu'avant le cache (ex. « Cours non trouvé »)
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw e;
        }
    }

    CourseResponse copy(CourseResponse course) {
        return course == null ? null : copier.convertValue(course, CourseResponse.class);
    }

    /** Poids d'une valeur gardée : environ un objet par cours, chapitre, leçon et ressource (voir CacheConfig). */
    public static int weight(Object value) {
        if (value instanceof CachedPage page) {
            return 1 + page.content().stream().mapToInt(CourseCatalogCache::courseWeight).sum();
        }
        if (value instanceof CourseResponse course) return courseWeight(course);
        return 1;
    }

    private static int courseWeight(CourseResponse course) {
        int weight = 1;
        if (course.getChapters() == null) return weight;
        for (var chapter : course.getChapters()) {
            weight++;
            if (chapter.getLessons() == null) continue;
            for (var lesson : chapter.getLessons()) {
                weight += 1 + (lesson.getResources() == null ? 0 : lesson.getResources().size());
            }
        }
        return weight;
    }

    record CachedPage(List<CourseResponse> content, long total) { }
}
