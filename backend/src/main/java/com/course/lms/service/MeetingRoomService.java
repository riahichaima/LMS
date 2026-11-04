package com.course.lms.service;

import com.course.lms.dto.MeetingDtos.ChatLine;
import com.course.lms.dto.MeetingDtos.Participant;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Salles de réunion en mémoire : qui est connecté (et depuis quelle session WebSocket),
 * l'état micro / caméra / partage / enregistrement de chacun et l'historique du chat.
 * La vidéo elle-même circule directement entre navigateurs (WebRTC) : le serveur ne relaie que la signalisation.
 */
@Service
public class MeetingRoomService {

    private static final int MAX_CHAT_HISTORY = 200;

    /** Participant connecté : identité + session WebSocket + état des médias. */
    public static final class Member {
        final Long userId;
        final String email;
        final String name;
        final String role;
        final String avatarUrl;
        volatile String sessionId;
        volatile boolean audio = true, video = true, screen = false, recording = false;

        Member(Long userId, String email, String name, String role, String avatarUrl, String sessionId) {
            this.userId = userId;
            this.email = email;
            this.name = name;
            this.role = role;
            this.avatarUrl = avatarUrl;
            this.sessionId = sessionId;
        }

        public Long userId() { return userId; }
        public String email() { return email; }

        public Participant view() {
            return new Participant(userId, name, role, avatarUrl, audio, video, screen, recording);
        }
    }

    /** Retrait d'un participant (départ ou déconnexion). */
    public record Removal(Long meetingId, Member member) {}

    private final Map<Long, Map<Long, Member>> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Deque<ChatLine>> chats = new ConcurrentHashMap<>();

    /** Entrée dans la salle ; renvoie les autres participants déjà présents. */
    public List<Member> join(Long meetingId, Long userId, String email, String name, String role, String avatarUrl, String sessionId) {
        Map<Long, Member> room = rooms.computeIfAbsent(meetingId, k -> new ConcurrentHashMap<>());
        room.put(userId, new Member(userId, email, name, role, avatarUrl, sessionId));
        return room.values().stream().filter(m -> !m.userId.equals(userId)).toList();
    }

    public Optional<Member> leave(Long meetingId, Long userId) {
        Map<Long, Member> room = rooms.get(meetingId);
        if (room == null) return Optional.empty();
        Member removed = room.remove(userId);
        cleanup(meetingId);
        return Optional.ofNullable(removed);
    }

    /** Session WebSocket fermée (onglet fermé, réseau coupé) : retrait de toutes ses salles. */
    public List<Removal> removeSession(String sessionId) {
        List<Removal> removed = new ArrayList<>();
        rooms.forEach((meetingId, room) -> room.values().removeIf(m -> {
            if (sessionId.equals(m.sessionId)) {
                removed.add(new Removal(meetingId, m));
                return true;
            }
            return false;
        }));
        removed.forEach(r -> cleanup(r.meetingId()));
        return removed;
    }

    public Optional<Member> member(Long meetingId, Long userId) {
        Map<Long, Member> room = rooms.get(meetingId);
        return room == null ? Optional.empty() : Optional.ofNullable(room.get(userId));
    }

    public Collection<Member> members(Long meetingId) {
        Map<Long, Member> room = rooms.get(meetingId);
        return room == null ? List.of() : List.copyOf(room.values());
    }

    public int count(Long meetingId) {
        Map<Long, Member> room = rooms.get(meetingId);
        return room == null ? 0 : room.size();
    }

    public void updateState(Member m, Boolean audio, Boolean video, Boolean screen, Boolean recording) {
        if (audio != null) m.audio = audio;
        if (video != null) m.video = video;
        if (screen != null) m.screen = screen;
        if (recording != null) m.recording = recording;
    }

    public ChatLine addChat(Long meetingId, Member author, String text) {
        ChatLine line = new ChatLine(UUID.randomUUID().toString(), author.userId, author.name, text, Instant.now());
        Deque<ChatLine> history = chats.computeIfAbsent(meetingId, k -> new ConcurrentLinkedDeque<>());
        history.addLast(line);
        while (history.size() > MAX_CHAT_HISTORY) history.pollFirst();
        return line;
    }

    public List<ChatLine> chatHistory(Long meetingId) {
        Deque<ChatLine> history = chats.get(meetingId);
        return history == null ? List.of() : List.copyOf(history);
    }

    /** Salle vide : la salle et son chat sont oubliés. */
    private void cleanup(Long meetingId) {
        Map<Long, Member> room = rooms.get(meetingId);
        if (room != null && room.isEmpty()) {
            rooms.remove(meetingId);
            chats.remove(meetingId);
        }
    }
}
