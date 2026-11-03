package com.course.lms.dto;

import java.time.Instant;
import java.util.List;

/** Réunions en visio : planification, participants en ligne, enregistrements. */
public final class MeetingDtos {

    private MeetingDtos() {}

    /** startAt : instant ISO-8601 (ex. 2026-11-02T14:30:00Z), envoyé par le navigateur dans son fuseau. */
    public record MeetingRequest(Long courseId, String title, String description, Instant startAt, Integer durationMinutes) {}

    /** status : SCHEDULED (à venir), LIVE (salle ouverte), ENDED (terminée). */
    public record MeetingView(Long id, Long courseId, String courseTitle, Long teacherId, String teacherName, String title,
                              String description, Instant startAt, Instant endAt, int durationMinutes, String status,
                              boolean canJoin, boolean canEdit, int participantsOnline, long recordingCount,
                              Instant joinOpensAt) {}

    public record RecordingView(Long id, Long meetingId, String meetingTitle, String courseTitle, Long recordedById,
                                String recordedByName, long sizeBytes, Integer durationSeconds, String mimeType,
                                Instant createdAt, boolean canDelete) {}

    public record IceServer(List<String> urls, String username, String credential) {}

    public record Participant(Long userId, String name, String role, String avatarUrl, boolean audio, boolean video,
                              boolean screen, boolean recording) {}

    public record ChatLine(String id, Long userId, String name, String text, Instant at) {}
}
