package com.course.lms.service;

import com.course.lms.dto.EnrollmentResponse;
import com.course.lms.model.Course;
import com.course.lms.model.Enrollment;
import com.course.lms.model.User;
import com.course.lms.repository.CourseRepository;
import com.course.lms.repository.EnrollmentRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnrollmentService {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    /**
     * Module Étudiant - Inscription à un cours
     */
    public EnrollmentResponse enrollStudent(Long courseId, String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (enrollmentRepository.findByStudentIdAndCourseId(student.getId(), courseId).isPresent()) {
            throw new RuntimeException("Étudiant déjà inscrit à ce cours");
        }
        // Un cours payant n'est accessible qu'après un paiement confirmé par Stripe (voir PaymentService)
        if (course.isPaid()) {
            throw new IllegalStateException("Ce cours est payant : utilisez « Buy Now » pour l'acheter.");
        }

        Enrollment enrollment = new Enrollment(student, course);
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);
        if (course.getTeacher() != null) {
            notificationService.sendNotification(course.getTeacher(), "Nouvel inscrit",
                    student.getName() + " s'est inscrit à votre cours « " + course.getTitle() + " ».", "ENROLLMENT", course.getId());
        }
        return mapToResponse(savedEnrollment);
    }

    /**
     * Inscription créée après un paiement Stripe confirmé côté serveur (webhook ou vérification API).
     * Sans effet si l'étudiant est déjà inscrit.
     */
    public void enrollAfterConfirmedPayment(User student, Course course) {
        if (enrollmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId()).isEmpty()) {
            enrollmentRepository.save(new Enrollment(student, course));
            systemLogService.record(com.course.lms.model.SystemLog.INFO, "PAYMENT", "Paiement confirmé",
                    student.getEmail() + " a acheté « " + course.getTitle() + " » (" + course.getPrice() + " $)", student.getEmail());
            notificationService.sendNotification(student, "Paiement confirmé",
                    "Votre achat du cours « " + course.getTitle() + " » est confirmé : bon apprentissage !", "PAYMENT", course.getId());
            if (course.getTeacher() != null) {
                notificationService.sendNotification(course.getTeacher(), "Nouvelle vente",
                        student.getName() + " a acheté votre cours « " + course.getTitle() + " ».", "SALE", course.getId());
            }
        }
    }

    /**
     * Module Étudiant - Suivi des cours inscrits
     */
    public List<EnrollmentResponse> getStudentEnrollments(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        return enrollmentRepository.findByStudentId(student.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Module Étudiant - Mise à jour de la progression
     */
    public EnrollmentResponse updateProgress(Long enrollmentId, Integer progressPercentage) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Inscription non trouvée"));

        enrollment.updateProgress(progressPercentage);
        Enrollment updated = enrollmentRepository.save(enrollment);
        return mapToResponse(updated);
    }

    /**
     * Module Étudiant - Marquer un cours comme terminé
     */
    public EnrollmentResponse markAsCompleted(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Inscription non trouvée"));

        enrollment.updateProgress(100);
        Enrollment updated = enrollmentRepository.save(enrollment);
        return mapToResponse(updated);
    }

    /**
     * Module Formateur - Liste des étudiants inscrits à un cours
     */
    public List<EnrollmentResponse> getCourseEnrollments(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        return enrollmentRepository.findByCourseId(courseId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Module Administrateur - Statistiques d'inscription
     */
    public Long getEnrollmentCount(Long courseId) {
        return enrollmentRepository.countByCourseId(courseId);
    }

    /**
     * Module Étudiant - Désinscription d'un cours
     */
    public void unenrollStudent(Long enrollmentId, String studentEmail) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Inscription non trouvée"));

        if (!enrollment.getStudent().getEmail().equals(studentEmail)) {
            throw new RuntimeException("Non autorisé à supprimer cette inscription");
        }

        enrollmentRepository.delete(enrollment);
    }

    /**
     * Module Administrateur - Supprimer une inscription
     */
    public void removeEnrollment(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Inscription non trouvée"));
        enrollmentRepository.delete(enrollment);
    }

    private EnrollmentResponse mapToResponse(Enrollment enrollment) {
        EnrollmentResponse response = new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getName(),
                enrollment.getStudent().getEmail(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getTitle(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt(),
                enrollment.getProgressPercentage(),
                enrollment.getIsCompleted(),
                enrollment.getLastAccessedAt()
        );
        User teacher = enrollment.getCourse().getTeacher();
        if (teacher != null) {
            response.setTeacherId(teacher.getId());
            response.setTeacherName(teacher.getName());
        }
        return response;
    }
}
