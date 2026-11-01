package com.course.lms.service;

import com.course.lms.model.User;

/** URL publique de la photo de profil d'un utilisateur (null = afficher ses initiales). */
public final class Avatars {

    public static final String BASE = "/api/profiles/avatars/";

    private Avatars() {}

    public static String url(User user) {
        return user == null || user.getAvatarPath() == null ? null : BASE + user.getAvatarPath();
    }
}
