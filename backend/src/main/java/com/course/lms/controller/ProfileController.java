package com.course.lms.controller;

import com.course.lms.dto.ProfileDtos.NamesRequest;
import com.course.lms.dto.ProfileDtos.ProfileView;
import com.course.lms.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * Profils :
 *   GET    /api/profiles/me                 → mon profil
 *   PUT    /api/profiles/me                 → modifier prénom / nom { firstName, lastName }
 *   POST   /api/profiles/me/avatar          → envoyer une photo (multipart « file »)
 *   DELETE /api/profiles/me/avatar          → retirer la photo
 *   GET    /api/profiles/{id}               → profil d'un utilisateur (selon les droits)
 *   GET    /api/profiles/avatars/{fichier}  → image publique (balise img, sans jeton)
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileView> me(Authentication auth) {
        return ResponseEntity.ok(profileService.me(auth.getName()));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileView> updateNames(@RequestBody NamesRequest request, Authentication auth) {
        return ResponseEntity.ok(profileService.updateNames(auth.getName(), request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileView> uploadAvatar(@RequestParam("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.ok(profileService.uploadAvatar(auth.getName(), file));
    }

    @DeleteMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileView> deleteAvatar(Authentication auth) {
        return ResponseEntity.ok(profileService.deleteAvatar(auth.getName()));
    }

    @GetMapping("/{userId:\\d+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileView> profile(@PathVariable Long userId, Authentication auth) {
        return ResponseEntity.ok(profileService.profile(userId, auth.getName()));
    }

    /** Public : une balise <img> n'envoie pas le jeton. Nom de fichier aléatoire, donc cache long. */
    @GetMapping("/avatars/{fileName:.+}")
    public ResponseEntity<byte[]> avatar(@PathVariable String fileName) {
        return profileService.avatar(fileName)
                .map(f -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(f.contentType()))
                        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                        .body(f.bytes()))
                .orElse(ResponseEntity.notFound().build());
    }
}
