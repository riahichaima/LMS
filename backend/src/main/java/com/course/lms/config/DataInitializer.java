package com.course.lms.config;

import com.course.lms.model.ApprovalStatus;
import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final List<String> ROLES = List.of("STUDENT", "TEACHER", "ADMIN", "SUPER_ADMIN");

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Compte super administrateur créé par défaut (à changer dans application.yml)
    @Value("${app.super-admin.email:superadmin@lms.com}")
    private String superAdminEmail;

    @Value("${app.super-admin.password:Lmssuperadmin}")
    private String superAdminPassword;

    @Value("${app.super-admin.name:Super Administrateur}")
    private String superAdminName;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        fixExistingUserRoles();
        seedSuperAdmin();
    }

    private void initializeRoles() {
        for (String name : ROLES) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(new Role(name));
                System.out.println("Rôle créé : " + name);
            }
        }
    }

    private void fixExistingUserRoles() {
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RuntimeException("STUDENT role not found"));

        // Les utilisateurs sans rôle valide deviennent étudiants
        userRepository.findAll().forEach(user -> {
            if (user.getRole() == null || !ROLES.contains(user.getRole().getName())) {
                user.setRole(studentRole);
                userRepository.save(user);
                System.out.println("Fixed role for user: " + user.getEmail() + " -> STUDENT");
            }
        });
    }

    // Il n'existe qu'un seul super administrateur : le compte configuré dans application.yml.
    // Ses identifiants y sont la référence (le mot de passe est réappliqué au démarrage).
    private void seedSuperAdmin() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        // Tout autre compte SUPER_ADMIN redevient administrateur
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "SUPER_ADMIN".equals(u.getRole().getName()))
                .filter(u -> !superAdminEmail.equalsIgnoreCase(u.getEmail()))
                .forEach(u -> {
                    u.setRole(adminRole);
                    u.setApprovalStatus(ApprovalStatus.APPROVED);
                    userRepository.save(u);
                    System.out.println("Ancien super administrateur rétrogradé en administrateur : " + u.getEmail());
                });

        User user = userRepository.findByEmail(superAdminEmail)
                .orElseGet(() -> new User(superAdminName, superAdminEmail, passwordEncoder.encode(superAdminPassword), superAdminRole));
        boolean created = user.getId() == null;
        user.setRole(superAdminRole);
        user.setEnabled(true);
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        if (!passwordEncoder.matches(superAdminPassword, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(superAdminPassword));
        }
        userRepository.save(user);
        System.out.println((created ? "Super administrateur créé : " : "Super administrateur : ") + superAdminEmail);
    }
}
