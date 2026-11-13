package com.course.lms.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Active le gestionnaire de cache (Caffeine, réglé dans application.yml : spring.cache). */
@Configuration
@EnableCaching
public class CacheConfig {
}
