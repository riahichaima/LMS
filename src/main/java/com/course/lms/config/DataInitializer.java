package com.course.lms.config;

import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        fixExistingUserRoles();
    }

    private void initializeRoles() {
        if (roleRepository.count() == 0) {
            Role studentRole = new Role("STUDENT");
            Role teacherRole = new Role("TEACHER");
            Role adminRole = new Role("ADMIN");

            roleRepository.save(studentRole);
            roleRepository.save(teacherRole);
            roleRepository.save(adminRole);

            System.out.println("Default roles initialized: STUDENT, TEACHER, ADMIN");
        } else {
            System.out.println("Roles already exist in database");
        }
    }
    
    private void fixExistingUserRoles() {
        // Récupérer les rôles valides
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RuntimeException("STUDENT role not found"));
        
        // Mettre à jour les utilisateurs qui n'ont pas de rôle valide
        userRepository.findAll().forEach(user -> {
            if (user.getRole() == null || 
                (!user.getRole().getName().equals("STUDENT") && 
                 !user.getRole().getName().equals("TEACHER") && 
                 !user.getRole().getName().equals("ADMIN"))) {
                
                user.setRole(studentRole);
                userRepository.save(user);
                System.out.println("Fixed role for user: " + user.getEmail() + " -> STUDENT");
            }
        });
    }
}
