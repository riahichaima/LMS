package com.course.lms.controller;

import com.course.lms.dto.*;
import com.course.lms.service.*;
import com.course.lms.model.*;
import com.course.lms.repository.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AuthService authService;

    @Autowired
    private com.course.lms.service.AccountDeletionService accountDeletionService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // ==========================================
    // MODULE ADMINISTRATEUR - Gestion des utilisateurs
    // ==========================================

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserWithRoleDetails>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsersWithRoleDetails());
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getUserById(id));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody RegisterRequest request) {
        requireSuperAdminForRole(request.getRole());
        UserResponse created = authService.register(request);
        if ("ADMIN".equals(request.getRole())) {
            // Créé directement par le super administrateur : pas besoin de validation
            userRepository.findById(created.getId()).ifPresent(u -> {
                u.setApprovalStatus(ApprovalStatus.APPROVED);
                userRepository.save(u);
            });
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody RegisterRequest request) {
        protectSuperAdmin(findUser(id));
        requireSuperAdminForRole(request.getRole());
        return ResponseEntity.ok(authService.updateUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication auth) {
        User target = findUser(id);
        protectSuperAdmin(target);
        if (target.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte ici.");
        }
        if (target.getRole() != null && "ADMIN".equals(target.getRole().getName()) && !currentUserIsSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le super administrateur peut supprimer un administrateur.");
        }
        try {
            accountDeletionService.deleteAccount(target, auth.getName());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable Long id) {
        protectSuperAdmin(findUser(id));
        return ResponseEntity.ok(authService.setUserEnabled(id, true));
    }

    @PutMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable Long id) {
        protectSuperAdmin(findUser(id));
        return ResponseEntity.ok(authService.setUserEnabled(id, false));
    }

    // Attribution des rôles
    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> assignRole(@PathVariable Long id, @RequestParam String roleName) {
        User user = findUser(id);
        protectSuperAdmin(user);
        requireSuperAdminForRole(roleName);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Rôle non trouvé"));
        user.setRole(role);
        // Un administrateur nommé par le super administrateur est validé d'office
        user.setApprovalStatus("ADMIN".equals(roleName) ? ApprovalStatus.APPROVED : null);
        userRepository.save(user);
        return ResponseEntity.ok(authService.getUserById(id));
    }

    // ── Règles réservées au super administrateur ──

    private static final Set<String> PRIVILEGED_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    private boolean currentUserIsSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    // Le compte super administrateur ne peut être ni modifié, ni désactivé, ni supprimé depuis l'administration
    private void protectSuperAdmin(User target) {
        if (target.getRole() != null && "SUPER_ADMIN".equals(target.getRole().getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte super administrateur ne peut pas être modifié ici.");
        }
    }

    // Seul le super administrateur peut donner le rôle ADMIN ; personne ne peut donner SUPER_ADMIN
    private void requireSuperAdminForRole(String roleName) {
        if ("SUPER_ADMIN".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le rôle super administrateur ne peut pas être attribué.");
        }
        if (roleName != null && PRIVILEGED_ROLES.contains(roleName) && !currentUserIsSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le super administrateur peut attribuer le rôle administrateur.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", String.valueOf(ex.getReason())));
    }

    // ==========================================
    // MODULE ADMINISTRATEUR - Gestion des formations
    // ==========================================

    @PostMapping("/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.createCourse(request, "admin@lms.com"));
    }

    @GetMapping("/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        // Retourne TOUTES les formations pour l'admin
        return ResponseEntity.ok(courseService.getAllCourses(org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(id, request, "admin@lms.com", true));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id, "admin@lms.com", true);
        return ResponseEntity.ok("Cours supprimé avec succès");
    }

    @PutMapping("/courses/{courseId}/assign-teacher/{teacherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseResponse> assignTeacherToCourse(@PathVariable Long courseId, @PathVariable Long teacherId) {
        return ResponseEntity.ok(courseService.assignTeacherToCourse(courseId, teacherId));
    }

    // Statistiques
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminStatistics> getStatistics() {
        long totalUsers = userRepository.count();
        long totalCourses = courseRepository.count();
        long totalEnrollments = enrollmentRepository.count();
        
        return ResponseEntity.ok(new AdminStatistics(totalUsers, totalCourses, totalEnrollments));
    }

    // Classe interne pour les statistiques
    public static class AdminStatistics {
        private long totalUsers;
        private long totalCourses;
        private long totalEnrollments;

        public AdminStatistics(long totalUsers, long totalCourses, long totalEnrollments) {
            this.totalUsers = totalUsers;
            this.totalCourses = totalCourses;
            this.totalEnrollments = totalEnrollments;
        }

        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getTotalCourses() { return totalCourses; }
        public long getTotalEnrollments() { return totalEnrollments; }
    }
}
