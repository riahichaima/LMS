package com.course.lms.service;

import com.course.lms.dto.AssessmentFileDtos.*;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Quiz et TP importés par l'enseignant (fichier préparé sur son ordinateur) : l'étudiant inscrit télécharge
 * le sujet et rend sa copie ; l'enseignant télécharge les copies et les note sur 20 (l'étudiant est notifié).
 */
@Service
@Transactional
public class AssessmentFileService {

    static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final java.time.format.DateTimeFormatter DUE = java.time.format.DateTimeFormatter
            .ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(java.time.ZoneId.of("Africa/Tunis"));
    private static final Path DIR = Paths.get("uploads", "assessments").toAbsolutePath().normalize();
    private static final Pattern STORED_NAME = Pattern.compile("^[a-f0-9]{32}\\.[a-z0-9]{2,5}$");

    private final AssessmentFileRepository fileRepository;
    private final AssessmentFileSubmissionRepository submissionRepository;
    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AssessmentScheduleService scheduleService;
    private final AssessmentAttemptRepository attemptRepository;

    public AssessmentFileService(AssessmentFileRepository fileRepository, AssessmentFileSubmissionRepository submissionRepository,
                                 ChapterRepository chapterRepository, CourseRepository courseRepository,
                                 EnrollmentRepository enrollmentRepository, UserRepository userRepository,
                                 NotificationService notificationService, AssessmentScheduleService scheduleService,
                                 AssessmentAttemptRepository attemptRepository) {
        this.scheduleService = scheduleService;
        this.attemptRepository = attemptRepository;
        this.fileRepository = fileRepository;
        this.submissionRepository = submissionRepository;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ── Enseignant ──────────────────────────────────────────────

    public AssessmentFileView upload(String email, Long chapterId, String type, String title, String instructions,
                                     String level, MultipartFile file) {
        return upload(email, chapterId, type, title, instructions, level, file, null, null);
    }

    /** dueAt : date limite de rendu ; durationMinutes : temps de travail après « Commencer » (null = sans limite). */
    public AssessmentFileView upload(String email, Long chapterId, String type, String title, String instructions,
                                     String level, MultipartFile file, java.time.Instant dueAt, Integer durationMinutes) {
        User teacher = findUser(email);
        if (chapterId == null) throw new IllegalArgumentException("Choisissez le chapitre.");
        Chapter chapter = chapterRepository.findById(chapterId).orElseThrow(() -> new IllegalArgumentException("Chapitre introuvable."));
        Course course = chapter.getCourse();
        if (course.getTeacher() == null || !course.getTeacher().getId().equals(teacher.getId())) {
            throw new SecurityException("Vous ne pouvez ajouter des quiz et TP qu'à vos propres cours.");
        }
        String kind = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!AssessmentFile.QUIZ.equals(kind) && !AssessmentFile.TP.equals(kind)) throw new IllegalArgumentException("Type invalide : quiz ou TP.");
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.length() < 3 || cleanTitle.length() > 150) throw new IllegalArgumentException("Le titre doit contenir entre 3 et 150 caractères.");
        String cleanInstructions = instructions == null || instructions.isBlank() ? null : instructions.trim();
        if (cleanInstructions != null && cleanInstructions.length() > 1000) throw new IllegalArgumentException("Consignes trop longues (1000 caractères maximum).");
        scheduleService.validateForFile(dueAt, durationMinutes);

        Stored stored = store(file);
        AssessmentFile af = new AssessmentFile();
        af.setChapter(chapter);
        af.setCourseId(course.getId());
        af.setTeacher(teacher);
        af.setType(kind);
        af.setTitle(cleanTitle);
        af.setInstructions(cleanInstructions);
        af.setDifficultyLevel(level(level));
        af.setFileName(stored.fileName());
        af.setOriginalName(stored.originalName());
        af.setMimeType(stored.mimeType());
        af.setSizeBytes(stored.size());
        af.setDueAt(dueAt);
        af.setDurationMinutes(durationMinutes);
        fileRepository.save(af);

        String label = AssessmentFile.QUIZ.equals(kind) ? "quiz" : "TP";
        String rules = (dueAt != null ? " À rendre avant le " + DUE.format(dueAt) + "." : "")
                + (durationMinutes != null ? " Temps de travail : " + AssessmentScheduleService.formatDuration(durationMinutes) + "." : "");
        for (Enrollment e : enrollmentRepository.findByCourseId(course.getId())) {
            notificationService.sendNotification(e.getStudent(), "📝 Nouveau " + label + " : « " + cleanTitle + " »",
                    teacher.getName() + " a ajouté un " + label + " au cours « " + course.getTitle() + " ». Téléchargez le sujet et rendez votre copie." + rules,
                    "ASSESSMENT_FILE", course.getId());
        }
        return view(af, null, true);
    }

    @Transactional(readOnly = true)
    public List<AssessmentFileView> teacherFiles(String email) {
        User teacher = findUser(email);
        return fileRepository.findByTeacherIdOrderByCreatedAtDescIdDesc(teacher.getId()).stream().map(f -> view(f, null, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionView> submissions(String email, Long fileId) {
        AssessmentFile f = owned(email, fileId);
        return submissionRepository.findByAssessmentFileIdOrderBySubmittedAtDesc(f.getId()).stream().map(this::submissionView).toList();
    }

    public SubmissionView grade(String email, Long submissionId, GradeRequest request) {
        AssessmentFileSubmission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Copie introuvable."));
        owned(email, s.getAssessmentFile().getId());
        if (request == null || request.grade() == null || request.grade() < 0 || request.grade() > 20) {
            throw new IllegalArgumentException("La note doit être comprise entre 0 et 20.");
        }
        String feedback = request.feedback() == null || request.feedback().isBlank() ? null : request.feedback().trim();
        if (feedback != null && feedback.length() > 2000) throw new IllegalArgumentException("Commentaire trop long (2000 caractères maximum).");
        s.setGrade(Math.round(request.grade() * 4) / 4.0); // au quart de point
        s.setFeedback(feedback);
        s.setGradedAt(LocalDateTime.now());
        AssessmentFile f = s.getAssessmentFile();
        notificationService.sendNotification(s.getStudent(), "✅ Copie corrigée : " + f.getTitle(),
                "Note : " + formatGrade(s.getGrade()) + "/20" + (feedback != null ? " — « " + feedback + " »" : ""), "ASSESSMENT_GRADED", f.getCourseId());
        return submissionView(s);
    }

    /** Suppression impossible dès qu'une copie a été rendue (pour ne pas perdre le travail des étudiants). */
    public void delete(String email, Long fileId) {
        AssessmentFile f = owned(email, fileId);
        if (submissionRepository.countByAssessmentFileId(fileId) > 0) {
            throw new IllegalStateException("Des étudiants ont déjà rendu leur copie : ce sujet ne peut plus être supprimé.");
        }
        attemptRepository.deleteByKindAndAssessmentIds(AssessmentAttempt.FILE, List.of(f.getId()));
        fileRepository.delete(f);
        deleteQuietly(f.getFileName());
    }

    // ── Étudiant ────────────────────────────────────────────────

    /** Sujets d'un cours (étudiant inscrit), avec sa copie éventuelle. */
    @Transactional(readOnly = true)
    public List<AssessmentFileView> courseFiles(User student, Long courseId) {
        return fileRepository.findByCourseIdOrderByCreatedAtAscIdAsc(courseId).stream()
                .map(f -> view(f, submissionRepository.findByAssessmentFileIdAndStudentId(f.getId(), student.getId()).orElse(null), false, student))
                .toList();
    }

    /** Dépôt (ou remplacement tant que la copie n'est pas corrigée) de la copie de l'étudiant. */
    public SubmissionView submit(String email, Long fileId, MultipartFile file) {
        User student = findUser(email);
        AssessmentFile f = fileRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("Sujet introuvable."));
        if (enrollmentRepository.findByStudentIdAndCourseId(student.getId(), f.getCourseId()).isEmpty()) {
            throw new SecurityException("Inscrivez-vous au cours pour rendre une copie.");
        }
        AssessmentFileSubmission s = submissionRepository.findByAssessmentFileIdAndStudentId(fileId, student.getId()).orElse(null);
        if (s != null && s.getGrade() != null) throw new IllegalStateException("Votre copie a déjà été corrigée : elle ne peut plus être remplacée.");
        // Date limite / chrono : dépôt et remplacement possibles jusqu'à la fin du temps
        scheduleService.checkFileUpload(student, f);

        Stored stored = store(file);
        String previous = null;
        if (s == null) {
            s = new AssessmentFileSubmission();
            s.setAssessmentFile(f);
            s.setStudent(student);
        } else {
            previous = s.getFileName();
        }
        s.setFileName(stored.fileName());
        s.setOriginalName(stored.originalName());
        s.setMimeType(stored.mimeType());
        s.setSizeBytes(stored.size());
        s.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(s);
        if (previous != null) deleteQuietly(previous);

        notificationService.sendNotification(f.getTeacher(), "📥 Nouvelle copie : " + f.getTitle(),
                student.getName() + " a rendu sa copie (" + stored.originalName() + ").", "ASSESSMENT_SUBMISSION", f.getId());
        return submissionView(s);
    }

    // ── Téléchargements ─────────────────────────────────────────

    /** Sujet : enseignant du cours, étudiant inscrit ou administration. */
    @Transactional(readOnly = true)
    public Download downloadSubject(String email, Long fileId) {
        User user = findUser(email);
        AssessmentFile f = fileRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("Sujet introuvable."));
        boolean allowed = f.getTeacher().getId().equals(user.getId()) || isStaff(user)
                || enrollmentRepository.findByStudentIdAndCourseId(user.getId(), f.getCourseId()).isPresent();
        if (!allowed) throw new SecurityException("Sujet réservé aux étudiants inscrits au cours.");
        // Sujet minuté : téléchargeable une fois le chrono lancé
        if (!f.getTeacher().getId().equals(user.getId()) && !isStaff(user)) {
            scheduleService.requireStartedIfTimed(user, AssessmentAttempt.FILE, f.getId());
        }
        return new Download(path(f.getFileName()), f.getOriginalName(), f.getMimeType());
    }

    /** Copie : son auteur, l'enseignant du cours ou l'administration. */
    @Transactional(readOnly = true)
    public Download downloadSubmission(String email, Long submissionId) {
        User user = findUser(email);
        AssessmentFileSubmission s = submissionRepository.findById(submissionId).orElseThrow(() -> new IllegalArgumentException("Copie introuvable."));
        boolean allowed = s.getStudent().getId().equals(user.getId()) || isStaff(user)
                || s.getAssessmentFile().getTeacher().getId().equals(user.getId());
        if (!allowed) throw new SecurityException("Copie réservée à son auteur et à l'enseignant.");
        return new Download(path(s.getFileName()), s.getOriginalName(), s.getMimeType());
    }

    // ── Outils ──────────────────────────────────────────────────

    private record Stored(String fileName, String originalName, String mimeType, long size) {}

    /** Vérifie le contenu réel du fichier (et pas seulement son extension), puis l'enregistre sous un nom aléatoire. */
    private Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choisissez un fichier.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Fichier trop volumineux (25 Mo maximum).");
        String original = sanitizeName(file.getOriginalFilename());
        String ext = extension(original);
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Lecture du fichier impossible.");
        }
        String mime = detect(head, ext);
        if (mime == null) {
            throw new IllegalArgumentException("Format non accepté : PDF, Word, PowerPoint, Excel, texte, image ou archive ZIP.");
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.createDirectories(DIR);
            try (InputStream in = file.getInputStream()) {
                Path target = DIR.resolve(stored).normalize();
                if (!STORED_NAME.matcher(stored).matches() || !target.startsWith(DIR)) {
                    throw new IllegalArgumentException("Nom de fichier invalide.");
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Enregistrement du fichier impossible.");
        }
        return new Stored(stored, original, mime, file.getSize());
    }

    static String detect(byte[] h, String ext) {
        if (h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F') return "pdf".equals(ext) ? "application/pdf" : null;
        boolean zip = h.length >= 4 && h[0] == 'P' && h[1] == 'K' && h[2] == 3 && h[3] == 4;
        if (zip) return switch (ext) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "zip" -> "application/zip";
            default -> null;
        };
        boolean ole = h.length >= 8 && (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF && (h[2] & 0xFF) == 0x11 && (h[3] & 0xFF) == 0xE0;
        if (ole) return switch (ext) {
            case "doc" -> "application/msword";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "xls" -> "application/vnd.ms-excel";
            default -> null;
        };
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return Set.of("jpg", "jpeg").contains(ext) ? "image/jpeg" : null;
        if (h.length >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png".equals(ext) ? "image/png" : null;
        if (Set.of("txt", "md", "java", "py", "js", "ts", "c", "cpp", "sql", "html", "css", "json").contains(ext)) {
            for (byte b : h) if (b == 0) return null; // binaire déguisé en texte
            return "text/plain";
        }
        return null;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeName(String name) {
        String base = name == null ? "fichier" : Paths.get(name).getFileName().toString();
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.isEmpty()) base = "fichier";
        byte[] bytes = base.getBytes(StandardCharsets.UTF_8);
        return bytes.length > 200 ? base.substring(0, 150) : base;
    }

    private Path path(String storedName) {
        Path p = DIR.resolve(storedName).normalize();
        if (!STORED_NAME.matcher(storedName).matches() || !p.startsWith(DIR) || !Files.isRegularFile(p)) {
            throw new IllegalArgumentException("Fichier introuvable.");
        }
        return p;
    }

    private void deleteQuietly(String storedName) {
        try {
            if (STORED_NAME.matcher(storedName).matches()) Files.deleteIfExists(DIR.resolve(storedName));
        } catch (IOException ignored) {
            // fichier déjà absent
        }
    }

    private AssessmentFile owned(String email, Long fileId) {
        User user = findUser(email);
        AssessmentFile f = fileRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("Sujet introuvable."));
        if (!f.getTeacher().getId().equals(user.getId())) throw new SecurityException("Ce sujet appartient à un autre enseignant.");
        return f;
    }

    private AssessmentFileView view(AssessmentFile f, AssessmentFileSubmission mine, boolean teacherView) {
        return view(f, mine, teacherView, null);
    }

    private AssessmentFileView view(AssessmentFile f, AssessmentFileSubmission mine, boolean teacherView, User student) {
        Chapter ch = f.getChapter();
        String courseTitle = courseRepository.findById(f.getCourseId()).map(Course::getTitle).orElse("");
        long count = 0, graded = 0;
        if (teacherView) {
            List<AssessmentFileSubmission> subs = submissionRepository.findByAssessmentFileIdOrderBySubmittedAtDesc(f.getId());
            count = subs.size();
            graded = subs.stream().filter(s -> s.getGrade() != null).count();
        }
        return new AssessmentFileView(f.getId(), f.getType(), f.getTitle(), f.getInstructions(), f.getDifficultyLevel().name(),
                f.getCourseId(), courseTitle, ch.getId(), ch.getTitle(), f.getOriginalName(), f.getMimeType(), f.getSizeBytes(),
                f.getCreatedAt(), count, graded, mine == null ? null : submissionView(mine), f.getDueAt(), f.getDurationMinutes(),
                student == null ? null : scheduleService.summary(student, AssessmentAttempt.FILE, f.getId(), f.getDueAt(), f.getDurationMinutes()));
    }

    private SubmissionView submissionView(AssessmentFileSubmission s) {
        User st = s.getStudent();
        return new SubmissionView(s.getId(), st.getId(), st.getName(), st.getEmail(), s.getOriginalName(), s.getSizeBytes(),
                s.getSubmittedAt(), s.getGrade(), s.getFeedback(), s.getGradedAt());
    }

    private static DifficultyLevel level(String value) {
        try {
            return value == null || value.isBlank() ? DifficultyLevel.BEGINNER : DifficultyLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Niveau invalide : débutant, intermédiaire ou avancé.");
        }
    }

    private static String formatGrade(double g) {
        return g == Math.rint(g) ? String.valueOf((int) g) : String.valueOf(g).replace('.', ',');
    }

    private static boolean isStaff(User u) {
        String r = u.getRole() != null ? u.getRole().getName() : "";
        return "ADMIN".equals(r) || "SUPER_ADMIN".equals(r);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
    }
}
