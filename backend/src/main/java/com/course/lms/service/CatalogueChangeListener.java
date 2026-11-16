package com.course.lms.service;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Écouteur JPA posé sur Course, Chapter, Lesson, LessonResource et User : toute création,
 * modification ou suppression vide le cache du catalogue, quel que soit le service qui l'a faite.
 */
public class CatalogueChangeListener {

    @PostPersist
    @PostUpdate
    @PostRemove
    public void changed(Object entity) {
        CourseCatalogCache.clearActive();
    }
}
