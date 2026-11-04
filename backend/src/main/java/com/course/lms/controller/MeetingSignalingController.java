package com.course.lms.controller;

import com.course.lms.dto.MeetingDtos.ChatLine;
import com.course.lms.model.User;
import com.course.lms.service.Avatars;
import com.course.lms.service.MeetingRoomService;
import com.course.lms.service.MeetingRoomService.Member;
import com.course.lms.service.MeetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.*;

/**
 * Signalisation des réunions (STOMP) : le serveur fait se « rencontrer » les navigateurs, qui échangent
 * ensuite leur vidéo directement (WebRTC). Tout passe par la file privée /user/queue/meeting.
 *
 *   /app/meeting.join   { meetingId }                    → welcome (participants + chat) / peer-joined aux autres
 *   /app/meeting.signal { meetingId, to, kind, data }    → offre / réponse / candidat ICE vers un participant
 *   /app/meeting.chat   { meetingId, text }              → message de chat à toute la salle
 *   /app/meeting.state  { meetingId, audio, video, screen, recording } → état des médias (icônes, « REC »)
 *   /app/meeting.leave  { meetingId }                    → peer-left aux autres
 */
@Controller
public class MeetingSignalingController {

    private static final Logger log = LoggerFactory.getLogger(MeetingSignalingController.class);
    private static final String QUEUE = "/queue/meeting";

    @Autowired private MeetingService meetingService;
    @Autowired private MeetingRoomService rooms;
    @Autowired private SimpMessagingTemplate messaging;

    @MessageMapping("/meeting.join")
    public void join(@Payload Map<String, Object> body, Principal principal, @Header("simpSessionId") String sessionId) {
        Long meetingId = longOf(body.get("meetingId"));
        try {
            User user = meetingService.requireJoinable(meetingId, principal.getName());
            List<Member> others = rooms.join(meetingId, user.getId(), user.getEmail(), user.getName(),
                    user.getRole() != null ? user.getRole().getName() : null, Avatars.url(user), sessionId);
            Member self = rooms.member(meetingId, user.getId()).orElseThrow();

            Map<String, Object> welcome = new LinkedHashMap<>();
            welcome.put("type", "welcome");
            welcome.put("meetingId", meetingId);
            welcome.put("self", self.view());
            welcome.put("peers", others.stream().map(Member::view).toList());
            welcome.put("chat", rooms.chatHistory(meetingId));
            send(principal.getName(), welcome);

            Map<String, Object> joined = event("peer-joined", meetingId);
            joined.put("peer", self.view());
            broadcast(meetingId, joined, user.getId());
        } catch (RuntimeException e) {
            sendError(principal.getName(), meetingId, e.getMessage());
        }
    }

    @MessageMapping("/meeting.signal")
    public void signal(@Payload Map<String, Object> body, Principal principal) {
        Long meetingId = longOf(body.get("meetingId"));
        Optional<Member> from = memberOf(meetingId, principal);
        Optional<Member> to = from.flatMap(f -> rooms.member(meetingId, longOf(body.get("to"))));
        if (from.isEmpty() || to.isEmpty()) return; // participant absent : signal ignoré
        Map<String, Object> out = event("signal", meetingId);
        out.put("from", from.get().userId());
        out.put("kind", String.valueOf(body.get("kind")));
        out.put("data", body.get("data"));
        send(to.get().email(), out);
    }

    @MessageMapping("/meeting.chat")
    public void chat(@Payload Map<String, Object> body, Principal principal) {
        Long meetingId = longOf(body.get("meetingId"));
        Optional<Member> author = memberOf(meetingId, principal);
        String text = body.get("text") == null ? "" : String.valueOf(body.get("text")).trim();
        if (author.isEmpty() || text.isEmpty()) return;
        if (text.length() > 1000) text = text.substring(0, 1000);
        ChatLine line = rooms.addChat(meetingId, author.get(), text);
        Map<String, Object> out = event("chat", meetingId);
        out.put("message", line);
        broadcast(meetingId, out, null);
    }

    @MessageMapping("/meeting.state")
    public void state(@Payload Map<String, Object> body, Principal principal) {
        Long meetingId = longOf(body.get("meetingId"));
        memberOf(meetingId, principal).ifPresent(m -> {
            rooms.updateState(m, bool(body.get("audio")), bool(body.get("video")), bool(body.get("screen")), bool(body.get("recording")));
            Map<String, Object> out = event("state", meetingId);
            out.put("peer", m.view());
            broadcast(meetingId, out, null);
        });
    }

    @MessageMapping("/meeting.leave")
    public void leave(@Payload Map<String, Object> body, Principal principal) {
        Long meetingId = longOf(body.get("meetingId"));
        memberOf(meetingId, principal).flatMap(m -> rooms.leave(meetingId, m.userId()))
                .ifPresent(m -> peerLeft(meetingId, m.userId()));
    }

    /** Onglet fermé ou réseau coupé : les autres participants sont prévenus. */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        rooms.removeSession(event.getSessionId()).forEach(r -> peerLeft(r.meetingId(), r.member().userId()));
    }

    // ── Outils ──────────────────────────────────────────────────

    private void peerLeft(Long meetingId, Long userId) {
        Map<String, Object> out = event("peer-left", meetingId);
        out.put("userId", userId);
        broadcast(meetingId, out, null);
    }

    private Optional<Member> memberOf(Long meetingId, Principal principal) {
        if (meetingId == null || principal == null) return Optional.empty();
        return rooms.members(meetingId).stream().filter(m -> m.email().equals(principal.getName())).findFirst();
    }

    private void broadcast(Long meetingId, Map<String, Object> payload, Long exceptUserId) {
        for (Member m : rooms.members(meetingId)) {
            if (!m.userId().equals(exceptUserId)) send(m.email(), payload);
        }
    }

    private void send(String email, Map<String, Object> payload) {
        try {
            messaging.convertAndSendToUser(email, QUEUE, payload);
        } catch (Exception e) {
            log.warn("Message de réunion non envoyé à {} : {}", email, e.getMessage());
        }
    }

    private void sendError(String email, Long meetingId, String message) {
        Map<String, Object> out = event("error", meetingId);
        out.put("message", message == null ? "Accès à la réunion impossible." : message);
        send(email, out);
    }

    private static Map<String, Object> event(String type, Long meetingId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("meetingId", meetingId);
        return m;
    }

    private static Long longOf(Object v) {
        if (v instanceof Number n) return n.longValue();
        try {
            return v == null ? null : Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Object v) {
        return v instanceof Boolean b ? b : null;
    }
}
