package com.course.lms.service;

import com.course.lms.dto.FeedbackDtos.TeacherProfile;
import com.course.lms.dto.ProfileDtos.*;
import com.course.lms.model.Enrollment;
import com.course.lms.model.User;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Profils : identité (prénom, nom, photo, rôle, ancienneté) pour tous les rôles,
 * cours et formateurs suivis pour un étudiant, statistiques et avis pour un formateur.
 *
 * Visibilité : profil formateur → tout utilisateur connecté ; profil étudiant → l'étudiant,
 * l'administration et ses formateurs (limité à leurs cours) ; administrateurs → nom, rôle et photo.
 */
@Service
@Transactional
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final long MAX_AVATAR_BYTES = 3L * 1024 * 1024;
    private static final Path AVATAR_DIR = Paths.get("uploads", "avatars").toAbsolutePath().normalize();
    private static final Pattern AVATAR_NAME = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|gif|webp)$");

    public record AvatarFile(byte[] bytes, String contentType) {}

    @Autowired private UserRepository userRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private FeedbackService feedbackService;

    @Transactional(readOnly = true)
    public ProfileView me(String email) {
        User user = findUser(email);
        return view(user, user);
    }

    @Transactional(readOnly = true)
    public ProfileView profile(Long userId, String viewerEmail) {
        User viewer = findUser(viewerEmail);
        User target = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        return view(target, viewer);
    }

    /** Prénom obligatoire, nom facultatif ; le nom complet affiché partout est mis à jour. */
    public ProfileView updateNames(String email, NamesRequest request) {
        User user = findUser(email);
        String first = clean(request == null ? null : request.firstName());
        String last = clean(request == null ? null : request.lastName());
        if (first == null) throw new IllegalArgumentException("Le prénom est obligatoire.");
        user.setFirstName(first);
        user.setLastName(last);
        user.setName(last == null ? first : first + " " + last);
        userRepository.save(user);
        return view(user, user);
    }

    /** Photo de profil : JPG, PNG, GIF ou WebP (contenu vérifié, pas seulement l'extension), 3 Mo maximum. */
    public ProfileView uploadAvatar(String email, MultipartFile file) {
        User user = findUser(email);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Aucune image reçue.");
        if (file.getSize() > MAX_AVATAR_BYTES) throw new IllegalArgumentException("Image trop lourde (3 Mo maximum).");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Lecture de l'image impossible.");
        }
        String ext = detectImageType(bytes);
        if (ext == null) throw new IllegalArgumentException("Format non supporté : utilisez une image JPG, PNG, GIF ou WebP.");

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.createDirectories(AVATAR_DIR);
            Files.write(AVATAR_DIR.resolve(fileName), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Enregistrement de l'image impossible.");
        }
        String previous = user.getAvatarPath();
        user.setAvatarPath(fileName);
        userRepository.save(user);
        deleteQuietly(previous);
        return view(user, user);
    }

    public ProfileView deleteAvatar(String email) {
        User user = findUser(email);
        String previous = user.getAvatarPath();
        user.setAvatarPath(null);
        userRepository.save(user);
        deleteQuietly(previous);
        return view(user, user);
    }

    /** Lecture d'une photo de profil (noms aléatoires uniquement : pas de parcours de répertoires). */
    @Transactional(readOnly = true)
    public Optional<AvatarFile> avatar(String fileName) {
        if (fileName == null || !AVATAR_NAME.matcher(fileName).matches()) return Optional.empty();
        Path path = AVATAR_DIR.resolve(fileName).normalize();
        if (!path.startsWith(AVATAR_DIR) || !Files.isRegularFile(path)) return Optional.empty();
        try {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
            String type = switch (ext) {
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                default -> "image/jpeg";
            };
            return Optional.of(new AvatarFile(Files.readAllBytes(path), type));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ── Construction du profil ──────────────────────────────────

    private ProfileView view(User target, User viewer) {
        String role = roleOf(target);
        boolean self = target.getId().equals(viewer.getId());
        boolean admin = isAdmin(viewer);

        StudentPart student = null;
        TeacherProfile teacher = null;
        if ("STUDENT".equals(role)) {
            boolean teacherOfStudent = "TEACHER".equals(roleOf(viewer))
                    && enrollmentRepository.existsStudentTeacherLink(target.getId(), viewer.getId());
            if (!self && !admin && !teacherOfStudent) {
                throw new SecurityException("Ce profil étudiant n'est visible que par l'étudiant, ses formateurs et l'administration.");
            }
            student = studentPart(target, self || admin ? null : viewer.getId());
        } else if ("TEACHER".equals(role)) {
            teacher = feedbackService.teacherProfile(target.getId());
        }

        String[] names = nameParts(target);
        return new ProfileView(target.getId(), names[0], names[1], target.getName(), role,
                self || admin ? target.getEmail() : null, Avatars.url(target), target.getCreatedAt(), self, student, teacher);
    }

    /** Cours suivis et formateurs de l'étudiant ; restrictToTeacherId = vue d'un formateur (ses cours seulement). */
    private StudentPart studentPart(User student, Long restrictToTeacherId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId()).stream()
                .filter(e -> restrictToTeacherId == null
                        || (e.getCourse().getTeacher() != null && restrictToTeacherId.equals(e.getCourse().getTeacher().getId())))
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .toList();

        List<StudentCourse> courses = new ArrayList<>();
        Map<Long, User> teachers = new LinkedHashMap<>();
        Map<Long, List<String>> teacherCourses = new HashMap<>();
        for (Enrollment e : enrollments) {
            User t = e.getCourse().getTeacher();
            courses.add(new StudentCourse(e.getCourse().getId(), e.getCourse().getTitle(), t != null ? t.getId() : null,
                    t != null ? t.getName() : null, e.getProgressPercentage() != null ? e.getProgressPercentage() : 0,
                    Boolean.TRUE.equals(e.getIsCompleted()), e.getEnrolledAt(), e.getLastAccessedAt()));
            if (t != null) {
                teachers.putIfAbsent(t.getId(), t);
                teacherCourses.computeIfAbsent(t.getId(), k -> new ArrayList<>()).add(e.getCourse().getTitle());
            }
        }
        List<FollowedTeacher> followed = teachers.values().stream()
                .map(t -> new FollowedTeacher(t.getId(), t.getName(), Avatars.url(t), teacherCourses.get(t.getId())))
                .toList();
        int avg = (int) Math.round(courses.stream().mapToInt(StudentCourse::progress).average().orElse(0));
        return new StudentPart(courses.size(), courses.stream().filter(StudentCourse::completed).count(), avg,
                courses, followed, restrictToTeacherId != null);
    }

    /** Prénom / nom enregistrés, sinon découpe du nom complet au premier espace. */
    private static String[] nameParts(User user) {
        if (user.getFirstName() != null) return new String[]{user.getFirstName(), user.getLastName()};
        String full = user.getName() == null ? "" : user.getName().trim();
        int space = full.indexOf(' ');
        return space < 0 ? new String[]{full, null} : new String[]{full.substring(0, space), full.substring(space + 1).trim()};
    }

    /** Signature binaire des images acceptées (le type annoncé par le navigateur n'est pas fiable). */
    static String detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    private void deleteQuietly(String fileName) {
        if (fileName == null || !AVATAR_NAME.matcher(fileName).matches()) return;
        try {
            Files.deleteIfExists(AVATAR_DIR.resolve(fileName));
        } catch (IOException e) {
            log.warn("Ancienne photo de profil non supprimée : {}", fileName);
        }
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() > 100) throw new IllegalArgumentException("100 caractères maximum.");
        return t;
    }

    private static String roleOf(User user) {
        return user.getRole() != null ? user.getRole().getName() : null;
    }

    private static boolean isAdmin(User user) {
        String role = roleOf(user);
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }
}
