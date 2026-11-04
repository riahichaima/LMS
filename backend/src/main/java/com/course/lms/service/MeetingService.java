package com.course.lms.service;

import com.course.lms.dto.MeetingDtos.*;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Réunions en visio : planification par le formateur, notifications aux étudiants du cours,
 * rappel automatique 1 h avant, contrôle d'accès à la salle et enregistrements.
 */
@Service
@Transactional
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);

    /** La salle ouvre 15 min avant le début et reste ouverte 1 h après la fin prévue. */
    public static final Duration OPENS_BEFORE = Duration.ofMinutes(15);
    public static final Duration CLOSES_AFTER = Duration.ofMinutes(60);
    private static final Duration REMINDER_BEFORE = Duration.ofHours(1);

    private static final Path RECORDING_DIR = Paths.get("uploads", "recordings").toAbsolutePath().normalize();
    private static final Pattern RECORDING_NAME = Pattern.compile("^[a-f0-9]{32}\\.(webm|mp4)$");

    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingRecordingRepository recordingRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private MeetingRoomService roomService;

    @Value("${app.timezone:Africa/Tunis}")
    private String timezone;

    // Serveur TURN facultatif (réseaux d'école / 4G qui bloquent la connexion directe entre navigateurs)
    @Value("${app.meetings.turn-url:}")
    private String turnUrl;
    @Value("${app.meetings.turn-username:}")
    private String turnUsername;
    @Value("${app.meetings.turn-credential:}")
    private String turnCredential;

    // ── Planification ───────────────────────────────────────────

    public MeetingView create(String email, MeetingRequest request) {
        User teacher = findUser(email);
        Course course = ownedCourse(request == null ? null : request.courseId(), teacher);
        Meeting meeting = new Meeting();
        meeting.setCourse(course);
        meeting.setTeacher(teacher);
        apply(meeting, request, true);
        meetingRepository.save(meeting);

        notifyStudents(meeting, "📅 Nouvelle réunion : " + meeting.getTitle(),
                teacher.getName() + " a planifié une réunion le " + when(meeting) + " (" + course.getTitle() + ").", "MEETING");
        return view(meeting, teacher);
    }

    public MeetingView update(Long id, String email, MeetingRequest request) {
        User teacher = findUser(email);
        Meeting meeting = findMeeting(id);
        requireOwner(meeting, teacher);
        if (Instant.now().isAfter(meeting.endAt())) throw new IllegalStateException("Une réunion terminée ne peut plus être modifiée.");
        Instant before = meeting.getStartAt();
        if (request != null && request.courseId() != null && !request.courseId().equals(meeting.getCourse().getId())) {
            meeting.setCourse(ownedCourse(request.courseId(), teacher));
        }
        apply(meeting, request, false);
        if (!before.equals(meeting.getStartAt())) meeting.setReminderSent(false);

        notifyStudents(meeting, "✏️ Réunion modifiée : " + meeting.getTitle(),
                "Nouvelle date : " + when(meeting) + " (" + meeting.getCourse().getTitle() + ").", "MEETING");
        return view(meeting, teacher);
    }

    /** Annulation : seulement avant le début (une réunion passée garde son historique et ses enregistrements). */
    public void cancel(Long id, String email) {
        User teacher = findUser(email);
        Meeting meeting = findMeeting(id);
        requireOwner(meeting, teacher);
        if (!Instant.now().isBefore(meeting.getStartAt())) {
            throw new IllegalStateException("La réunion a déjà commencé : elle ne peut plus être annulée.");
        }
        notifyStudents(meeting, "❌ Réunion annulée : " + meeting.getTitle(),
                "La réunion du " + when(meeting) + " (" + meeting.getCourse().getTitle() + ") est annulée.", "MEETING_CANCELLED");
        meetingRepository.delete(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingView> list(String email) {
        User user = findUser(email);
        return visibleMeetings(user).stream().map(m -> view(m, user)).toList();
    }

    @Transactional(readOnly = true)
    public MeetingView get(Long id, String email) {
        User user = findUser(email);
        Meeting meeting = findMeeting(id);
        requireAccess(meeting, user);
        return view(meeting, user);
    }

    /** Vérifie qu'on peut entrer dans la salle maintenant (appelé à la connexion WebSocket). */
    @Transactional(readOnly = true)
    public User requireJoinable(Long meetingId, String email) {
        User user = findUser(email);
        Meeting meeting = findMeeting(meetingId);
        requireAccess(meeting, user);
        Instant now = Instant.now();
        if (now.isBefore(meeting.getStartAt().minus(OPENS_BEFORE))) {
            throw new IllegalStateException("La salle ouvre 15 minutes avant le début de la réunion.");
        }
        if (now.isAfter(meeting.endAt().plus(CLOSES_AFTER))) {
            throw new IllegalStateException("Cette réunion est terminée.");
        }
        return user;
    }

    public List<IceServer> iceServers() {
        List<IceServer> servers = new ArrayList<>();
        servers.add(new IceServer(List.of("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"), null, null));
        if (turnUrl != null && !turnUrl.isBlank()) {
            servers.add(new IceServer(Arrays.stream(turnUrl.split(",")).map(String::trim).toList(), turnUsername, turnCredential));
        }
        return servers;
    }

    // ── Rappel automatique 1 h avant ────────────────────────────

    @Scheduled(fixedDelayString = "${app.meetings.reminder-check-ms:60000}", initialDelay = 20000)
    public void sendReminders() {
        Instant now = Instant.now();
        for (Meeting m : meetingRepository.findDueReminders(now.plus(REMINDER_BEFORE), now.minus(Duration.ofMinutes(5)))) {
            long minutes = Math.max(0, Duration.between(now, m.getStartAt()).toMinutes());
            String title = "⏰ Rappel : « " + m.getTitle() + " » " + (minutes <= 1 ? "commence maintenant" : "dans " + minutes + " min");
            String message = "Réunion du cours « " + m.getCourse().getTitle() + " » à " + time(m)
                    + ". La salle ouvre 15 minutes avant : cliquez pour la rejoindre.";
            notifyStudents(m, title, message, "MEETING_REMINDER");
            notificationService.sendNotification(m.getTeacher(), title, message, "MEETING_REMINDER", m.getId());
            m.setReminderSent(true);
            log.info("Rappel envoyé pour la réunion {} ({})", m.getId(), m.getTitle());
        }
    }

    // ── Enregistrements ─────────────────────────────────────────

    /** Vidéo WebM / MP4 (contenu vérifié) déposée par un participant à la fin de son enregistrement. */
    public RecordingView saveRecording(Long meetingId, String email, MultipartFile file, Integer durationSeconds) {
        User user = findUser(email);
        Meeting meeting = findMeeting(meetingId);
        requireAccess(meeting, user);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Enregistrement vide.");

        String ext;
        try (InputStream in = file.getInputStream()) {
            ext = detectVideo(in.readNBytes(12));
        } catch (IOException e) {
            throw new IllegalArgumentException("Lecture de l'enregistrement impossible.");
        }
        if (ext == null) throw new IllegalArgumentException("Format non supporté : vidéo WebM ou MP4 attendue.");

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.createDirectories(RECORDING_DIR);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, RECORDING_DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Enregistrement du fichier impossible.");
        }

        MeetingRecording rec = new MeetingRecording();
        rec.setMeeting(meeting);
        rec.setRecordedBy(user);
        rec.setFileName(fileName);
        rec.setMimeType("webm".equals(ext) ? "video/webm" : "video/mp4");
        rec.setSizeBytes(file.getSize());
        rec.setDurationSeconds(durationSeconds != null && durationSeconds >= 0 ? durationSeconds : null);
        recordingRepository.save(rec);

        // Tous les participants du cours (sauf l'auteur) sont prévenus
        String title = "🎬 Enregistrement disponible : " + meeting.getTitle();
        String message = user.getName() + " a enregistré la réunion du " + when(meeting) + ".";
        for (User u : courseParticipants(meeting)) {
            if (!u.getId().equals(user.getId())) notificationService.sendNotification(u, title, message, "RECORDING", meeting.getId());
        }
        return recordingView(rec, user);
    }

    @Transactional(readOnly = true)
    public List<RecordingView> recordings(String email, Long meetingId) {
        User user = findUser(email);
        List<Long> ids;
        if (meetingId != null) {
            Meeting meeting = findMeeting(meetingId);
            requireAccess(meeting, user);
            ids = List.of(meetingId);
        } else {
            ids = visibleMeetings(user).stream().map(Meeting::getId).toList();
        }
        if (ids.isEmpty()) return List.of();
        return recordingRepository.findByMeetingIdInOrderByCreatedAtDesc(ids).stream().map(r -> recordingView(r, user)).toList();
    }

    /** Fichier d'un enregistrement (participants du cours uniquement). */
    @Transactional(readOnly = true)
    public Path recordingFile(Long recordingId, String email) {
        User user = findUser(email);
        MeetingRecording rec = findRecording(recordingId);
        requireAccess(rec.getMeeting(), user);
        Path path = RECORDING_DIR.resolve(rec.getFileName()).normalize();
        if (!RECORDING_NAME.matcher(rec.getFileName()).matches() || !path.startsWith(RECORDING_DIR) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Fichier d'enregistrement introuvable.");
        }
        return path;
    }

    @Transactional(readOnly = true)
    public String recordingMimeType(Long recordingId) {
        return findRecording(recordingId).getMimeType();
    }

    /** Suppression : l'auteur de l'enregistrement, le formateur de la réunion ou un administrateur. */
    public void deleteRecording(Long recordingId, String email) {
        User user = findUser(email);
        MeetingRecording rec = findRecording(recordingId);
        if (!canDelete(rec, user)) throw new SecurityException("Vous ne pouvez pas supprimer cet enregistrement.");
        recordingRepository.delete(rec);
        try {
            if (RECORDING_NAME.matcher(rec.getFileName()).matches()) Files.deleteIfExists(RECORDING_DIR.resolve(rec.getFileName()));
        } catch (IOException e) {
            log.warn("Fichier d'enregistrement non supprimé : {}", rec.getFileName());
        }
    }

    // ── Vues et contrôles ───────────────────────────────────────

    private MeetingView view(Meeting m, User viewer) {
        Instant now = Instant.now();
        String status = now.isBefore(m.getStartAt().minus(OPENS_BEFORE)) ? "SCHEDULED"
                : now.isAfter(m.endAt()) ? "ENDED" : "LIVE";
        boolean open = !now.isBefore(m.getStartAt().minus(OPENS_BEFORE)) && !now.isAfter(m.endAt().plus(CLOSES_AFTER));
        boolean owner = m.getTeacher().getId().equals(viewer.getId());
        return new MeetingView(m.getId(), m.getCourse().getId(), m.getCourse().getTitle(), m.getTeacher().getId(),
                m.getTeacher().getName(), m.getTitle(), m.getDescription(), m.getStartAt(), m.endAt(), m.getDurationMinutes(),
                status, open, owner && now.isBefore(m.endAt()), roomService.count(m.getId()),
                recordingRepository.countByMeetingId(m.getId()), m.getStartAt().minus(OPENS_BEFORE));
    }

    private RecordingView recordingView(MeetingRecording r, User viewer) {
        Meeting m = r.getMeeting();
        return new RecordingView(r.getId(), m.getId(), m.getTitle(), m.getCourse().getTitle(), r.getRecordedBy().getId(),
                r.getRecordedBy().getName(), r.getSizeBytes(), r.getDurationSeconds(), r.getMimeType(), r.getCreatedAt(),
                canDelete(r, viewer));
    }

    private void apply(Meeting meeting, MeetingRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("Données de la réunion manquantes.");
        String title = request.title() == null ? "" : request.title().trim();
        if (title.length() < 3 || title.length() > 150) throw new IllegalArgumentException("Le sujet doit contenir entre 3 et 150 caractères.");
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        if (description != null && description.length() > 2000) throw new IllegalArgumentException("Description trop longue (2000 caractères maximum).");
        if (request.startAt() == null) throw new IllegalArgumentException("Choisissez la date et l'heure de la réunion.");
        Instant now = Instant.now();
        if (request.startAt().isBefore(now.minus(Duration.ofMinutes(5))) && (creating || !request.startAt().equals(meeting.getStartAt()))) {
            throw new IllegalArgumentException("La date de la réunion est déjà passée.");
        }
        if (request.startAt().isAfter(now.plus(Duration.ofDays(366)))) throw new IllegalArgumentException("Date trop lointaine (1 an maximum).");
        int duration = request.durationMinutes() == null ? 60 : request.durationMinutes();
        if (duration < 15 || duration > 480) throw new IllegalArgumentException("La durée doit être comprise entre 15 minutes et 8 heures.");
        meeting.setTitle(title);
        meeting.setDescription(description);
        meeting.setStartAt(request.startAt());
        meeting.setDurationMinutes(duration);
    }

    private List<Meeting> visibleMeetings(User user) {
        String role = roleOf(user);
        if ("TEACHER".equals(role)) return meetingRepository.findByTeacherIdOrderByStartAtAsc(user.getId());
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) return meetingRepository.findAllByOrderByStartAtAsc();
        List<Long> courseIds = enrollmentRepository.findByStudentId(user.getId()).stream().map(e -> e.getCourse().getId()).toList();
        return courseIds.isEmpty() ? List.of() : meetingRepository.findByCourseIdInOrderByStartAtAsc(courseIds);
    }

    private void requireAccess(Meeting m, User user) {
        String role = roleOf(user);
        boolean ok = m.getTeacher().getId().equals(user.getId())
                || "ADMIN".equals(role) || "SUPER_ADMIN".equals(role)
                || enrollmentRepository.findByStudentIdAndCourseId(user.getId(), m.getCourse().getId()).isPresent();
        if (!ok) throw new SecurityException("Réunion réservée au formateur et aux étudiants inscrits au cours.");
    }

    private void requireOwner(Meeting m, User user) {
        if (!m.getTeacher().getId().equals(user.getId())) throw new SecurityException("Seul le formateur qui a planifié la réunion peut la modifier.");
    }

    private Course ownedCourse(Long courseId, User teacher) {
        if (courseId == null) throw new IllegalArgumentException("Choisissez le cours concerné par la réunion.");
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
        if (course.getTeacher() == null || !course.getTeacher().getId().equals(teacher.getId())) {
            throw new SecurityException("Vous ne pouvez planifier une réunion que pour vos propres cours.");
        }
        return course;
    }

    private boolean canDelete(MeetingRecording r, User user) {
        String role = roleOf(user);
        return r.getRecordedBy().getId().equals(user.getId()) || r.getMeeting().getTeacher().getId().equals(user.getId())
                || "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private List<User> courseParticipants(Meeting m) {
        Map<Long, User> users = new LinkedHashMap<>();
        users.put(m.getTeacher().getId(), m.getTeacher());
        enrollmentRepository.findByCourseId(m.getCourse().getId()).forEach(e -> users.putIfAbsent(e.getStudent().getId(), e.getStudent()));
        return new ArrayList<>(users.values());
    }

    private void notifyStudents(Meeting m, String title, String message, String type) {
        enrollmentRepository.findByCourseId(m.getCourse().getId())
                .forEach(e -> notificationService.sendNotification(e.getStudent(), title, message, type, m.getId()));
    }

    /** « 02/11/2026 à 14:30 » dans le fuseau de la plateforme (texte des notifications). */
    private String when(Meeting m) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(zone()).format(m.getStartAt());
    }

    private String time(Meeting m) {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(zone()).format(m.getStartAt());
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("Africa/Tunis");
        }
    }

    /** Signature binaire : WebM/Matroska (1A 45 DF A3) ou MP4 (« ftyp » à l'octet 4). */
    static String detectVideo(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) return "webm";
        if (b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "mp4";
        return null;
    }

    private static String roleOf(User user) {
        return user.getRole() != null ? user.getRole().getName() : "";
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }

    private Meeting findMeeting(Long id) {
        return meetingRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Réunion introuvable."));
    }

    private MeetingRecording findRecording(Long id) {
        return recordingRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Enregistrement introuvable."));
    }
}
