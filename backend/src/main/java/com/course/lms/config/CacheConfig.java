package com.course.lms.config;

import com.course.lms.service.CourseCatalogCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache du catalogue (Caffeine). La taille est bornée en « poids » et non en nombre d'entrées :
 * environ un objet gardé par cours, chapitre, leçon et ressource. Quelles que soient les requêtes
 * reçues, la mémoire occupée reste plafonnée (50 000 objets par défaut, quelques dizaines de Mo).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public Caffeine<Object, Object> catalogueCaffeine(
            @Value("${app.cache.catalogue.max-weight:50000}") long maxWeight,
            @Value("${app.cache.catalogue.ttl-minutes:10}") long ttlMinutes) {
        return builder(maxWeight, Duration.ofMinutes(ttlMinutes));
    }

    /** Réglage partagé avec les tests. */
    public static Caffeine<Object, Object> builder(long maxWeight, Duration ttl) {
        return Caffeine.newBuilder()
                .maximumWeight(maxWeight)
                .weigher((Object key, Object value) -> CourseCatalogCache.weight(value))
                .expireAfterWrite(ttl)
                .recordStats();
    }
}
