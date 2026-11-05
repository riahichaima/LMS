package com.course.lms.service;

import com.course.lms.dto.ComplaintDtos.ComplaintRequest;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Suppression de comptes sur une vraie base MySQL : données liées, réclamations conservées, email réutilisable. */
@SpringBootTest
class AccountDeletionIntegrationTest {

    @Autowired AccountDeletionService deletion;
    @Autowired ComplaintService complaints;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired MessageRepository messages;
    @Autowired NotificationRepository notifications;
    @Autowired PaymentRepository payments;
    @Autowired WalletRepository wallets;
    @Autowired ComplaintRepository complaintRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void studentDeletesAccountWithReasonAndAddressCanBeReused() {
        User teacher = user("TEACHER");
        User student = user("STUDENT");
        Course course = course(teacher, "PUBLISHED");
        enroll(student, course);
        message(student, teacher);
        notify(student);
        complaints.create(student.getEmail(), new ComplaintRequest("TECHNIQUE", "Vidéo bloquée", "La vidéo du chapitre 2 ne démarre pas."));

        deletion.deleteOwnAccount(student.getEmail(), "site", "");

        assertThat(users.findById(student.getId())).isEmpty();
        assertThat(users.findByEmail(student.getEmail())).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM enrollments WHERE course_id = ?", course.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM messages WHERE receiver_id = ?", teacher.getId())).isZero();
        // Réclamations gardées pour l'administration, avec l'identité de l'auteur
        List<Complaint> kept = complaintRepository.findAll().stream()
                .filter(c -> student.getEmail().equals(c.getAuthorEmail())).toList();
        assertThat(kept).hasSize(2).allMatch(c -> c.getAuthor() == null);
        assertThat(kept).anyMatch(c -> ComplaintService.ACCOUNT_DELETION.equals(c.getCategory())
                && c.getMessage().contains("Le site ne me plaît pas"));
        assertThat(complaints.all()).anyMatch(v -> v.authorId() == null && v.authorName().contains("compte supprimé"));
        // Le cours de l'enseignant n'est pas touché
        assertThat(courses.findById(course.getId())).isPresent();
    }

    @Test
    void teacherWithoutSalesIsDeletedWithCourses() {
        User teacher = user("TEACHER");
        User student = user("STUDENT");
        Course course = course(teacher, "PUBLISHED");
        enroll(student, course);

        deletion.deleteOwnAccount(teacher.getEmail(), "AUTRE", "Je change de métier");

        assertThat(users.findById(teacher.getId())).isEmpty();
        assertThat(courses.findById(course.getId())).isEmpty();
        assertThat(users.findById(student.getId())).isPresent();
        // « Autre raison » : le texte écrit est l'objet ; l'administration peut clore sans réponse (personne à notifier)
        Complaint reason = complaintRepository.findAll().stream()
                .filter(c -> teacher.getEmail().equals(c.getAuthorEmail())).findFirst().orElseThrow();
        assertThat(reason.getSubject()).contains("enseignant").contains("Je change de métier");
        assertThat(complaints.handle(reason.getId(), "superadmin@lms.com",
                new com.course.lms.dto.ComplaintDtos.ComplaintUpdate("RESOLVED", null)).status()).isEqualTo("RESOLVED");
    }

    @Test
    void otherReasonNeedsAnExplanationAndStaffCannotUseIt() {
        User student = user("STUDENT");
        assertThatThrownBy(() -> deletion.deleteOwnAccount(student.getEmail(), "AUTRE", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deletion.deleteOwnAccount(student.getEmail(), "INCONNU", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deletion.reasonsFor(user("ADMIN").getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(deletion.reasonsFor(student.getEmail())).containsKey("PRIX").doesNotContainKey("REVENUS");
        assertThat(deletion.reasonsFor(user("TEACHER").getEmail())).containsKey("REVENUS").doesNotContainKey("PRIX");
        assertThat(users.findById(student.getId())).isPresent();
    }

    @Test
    void teacherWithMoneyLeftMustWithdrawFirst() {
        User teacher = user("TEACHER");
        Wallet wallet = new Wallet();
        wallet.setType("TEACHER");
        wallet.setOwner(teacher);
        wallet.setAvailableBalance(new BigDecimal("12.50"));
        wallets.save(wallet);

        assertThatThrownBy(() -> deletion.deleteOwnAccount(teacher.getEmail(), "REVENUS", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("retrait");
        assertThat(users.findById(teacher.getId())).isPresent();
    }

    @Test
    void studentWithPurchaseIsAnonymizedAndPaymentKept() {
        User teacher = user("TEACHER");
        User student = user("STUDENT");
        Course course = course(teacher, "PUBLISHED");
        Payment payment = new Payment();
        payment.setStudent(student);
        payment.setTeacher(teacher);
        payment.setCourse(course);
        payment.setAmount(new BigDecimal("20.00"));
        payment.setStatus(Payment.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payments.save(payment);
        String email = student.getEmail();

        deletion.deleteAccount(users.findById(student.getId()).orElseThrow(), "admin@test.lms");

        User anonymized = users.findById(student.getId()).orElseThrow();
        assertThat(anonymized.getEmail()).endsWith("@supprime.invalid");
        assertThat(anonymized.getName()).isEqualTo("Compte supprimé");
        assertThat(anonymized.isEnabled()).isFalse();
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(payments.findById(payment.getId())).isPresent();
    }

    // ── Données de test ──
    private User user(String role) {
        User u = new User();
        String id = UUID.randomUUID().toString().substring(0, 8);
        u.setName("Test " + role + " " + id);
        u.setEmail("suppression-" + id + "@test.lms");
        u.setPassword("x");
        u.setEnabled(true);
        u.setRole(roles.findByName(role).orElseThrow());
        return users.save(u);
    }

    private Course course(User teacher, String status) {
        Course c = new Course();
        c.setTitle("Cours de test");
        c.setStatus(status);
        c.setTeacher(teacher);
        return courses.save(c);
    }

    private void enroll(User student, Course course) {
        Enrollment e = new Enrollment();
        e.setStudent(student);
        e.setCourse(course);
        e.setEnrolledAt(LocalDateTime.now());
        enrollments.save(e);
    }

    private void message(User from, User to) {
        Message m = new Message();
        m.setSender(from);
        m.setReceiver(to);
        m.setContent("Bonjour");
        m.setSentAt(LocalDateTime.now());
        messages.save(m);
    }

    private void notify(User recipient) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setTitle("Test");
        n.setMessage("Test");
        n.setType("TEST");
        n.setCreatedAt(LocalDateTime.now());
        n.setIsRead(false);
        notifications.save(n);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
