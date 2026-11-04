package com.course.lms.controller;

import com.course.lms.dto.MeetingDtos.*;
import com.course.lms.service.MeetingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Réunions en visio :
 *   GET    /api/meetings                           → mes réunions (formateur : les siennes, étudiant : celles de ses cours)
 *   POST   /api/meetings                           → planifier (formateur) { courseId, title, description, startAt, durationMinutes }
 *   GET    /api/meetings/{id}                      → détail
 *   PUT    /api/meetings/{id}                      → modifier (formateur)
 *   DELETE /api/meetings/{id}                      → annuler avant le début (formateur)
 *   GET    /api/meetings/ice-servers               → serveurs STUN / TURN pour WebRTC
 *   GET    /api/meetings/recordings                → tous mes enregistrements (?meetingId=…)
 *   POST   /api/meetings/{id}/recordings           → déposer un enregistrement (multipart « file », durationSeconds)
 *   GET    /api/meetings/recordings/{id}/file      → lire / télécharger (avec jeton)
 *   DELETE /api/meetings/recordings/{id}           → supprimer
 * La salle elle-même passe par le WebSocket STOMP (/app/meeting.*), voir MeetingSignalingController.
 */
@RestController
@RequestMapping("/api/meetings")
@PreAuthorize("isAuthenticated()")
public class MeetingController {

    @Autowired
    private MeetingService meetingService;

    @GetMapping
    public ResponseEntity<List<MeetingView>> list(Authentication auth) {
        return ResponseEntity.ok(meetingService.list(auth.getName()));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<MeetingView> create(@RequestBody MeetingRequest request, Authentication auth) {
        return ResponseEntity.ok(meetingService.create(auth.getName(), request));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<MeetingView> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(meetingService.get(id, auth.getName()));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<MeetingView> update(@PathVariable Long id, @RequestBody MeetingRequest request, Authentication auth) {
        return ResponseEntity.ok(meetingService.update(id, auth.getName(), request));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication auth) {
        meetingService.cancel(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ice-servers")
    public ResponseEntity<List<IceServer>> iceServers() {
        return ResponseEntity.ok(meetingService.iceServers());
    }

    @GetMapping("/recordings")
    public ResponseEntity<List<RecordingView>> recordings(@RequestParam(required = false) Long meetingId, Authentication auth) {
        return ResponseEntity.ok(meetingService.recordings(auth.getName(), meetingId));
    }

    @PostMapping(value = "/{id:\\d+}/recordings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecordingView> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                @RequestParam(required = false) Integer durationSeconds, Authentication auth) {
        return ResponseEntity.ok(meetingService.saveRecording(id, auth.getName(), file, durationSeconds));
    }

    /** Spring gère les requêtes partielles (Range) : la vidéo peut être lue et parcourue sans tout télécharger. */
    @GetMapping("/recordings/{id:\\d+}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, Authentication auth) {
        Resource resource = new FileSystemResource(meetingService.recordingFile(id, auth.getName()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meetingService.recordingMimeType(id)))
                .body(resource);
    }

    @DeleteMapping("/recordings/{id:\\d+}")
    public ResponseEntity<Void> deleteRecording(@PathVariable Long id, Authentication auth) {
        meetingService.deleteRecording(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
