package com.course.lms.controller;

import com.course.lms.model.ApprovalStatus;
import com.course.lms.model.User;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    // Comptes administrateur avec leur statut de validation (en attente en premier)
    @GetMapping("/admins")
    public ResponseEntity<List<Map<String, Object>>> listAdmins() {
        List<Map<String, Object>> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "ADMIN".equals(u.getRole().getName()))
                .sorted((a, b) -> Integer.compare(order(a), order(b)))
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(admins);
    }

    @PutMapping("/admins/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        User user = findAdmin(id);
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    // Demande refusée : le compte est supprimé (la personne peut se réinscrire avec un autre rôle)
    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id) {
        User user = findAdmin(id);
        authService.deleteUser(user.getId());
        return ResponseEntity.ok(Map.of("id", id, "deleted", true));
    }

    private User findAdmin(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (user.getRole() == null || !"ADMIN".equals(user.getRole().getName())) {
            throw new RuntimeException("Ce compte n'est pas un compte administrateur");
        }
        return user;
    }

    private int order(User u) {
        return ApprovalStatus.PENDING.equals(u.getApprovalStatus()) ? 0 : 1;
    }

    private Map<String, Object> toDto(User u) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", u.getId());
        dto.put("name", u.getName());
        dto.put("email", u.getEmail());
        dto.put("enabled", u.isEnabled());
        // Les anciens comptes admin (sans statut) sont considérés comme validés
        dto.put("approvalStatus", u.getApprovalStatus() != null ? u.getApprovalStatus() : ApprovalStatus.APPROVED);
        return dto;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", String.valueOf(ex.getMessage())));
    }
}
