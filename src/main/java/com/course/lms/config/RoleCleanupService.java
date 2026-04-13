package com.course.lms.config;

import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoleCleanupService implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        displayRoleStatistics();
    }

    private void displayRoleStatistics() {
        System.out.println("� Affichage des statistiques des rôles...");
        
        // 1. Récupérer tous les rôles
        List<Role> allRoles = roleRepository.findAll();
        System.out.println("Found " + allRoles.size() + " roles in database");
        
        // 2. Afficher tous les rôles avec leurs utilisateurs
        for (Role role : allRoles) {
            List<User> usersWithThisRole = userRepository.findByRoleId(role.getId());
            System.out.println("🔹 Role ID " + role.getId() + " (" + role.getName() + ") a " + 
                usersWithThisRole.size() + " utilisateur(s):");
            
            for (User user : usersWithThisRole) {
                System.out.println("   - User ID " + user.getId() + ": " + user.getName() + " (" + user.getEmail() + ")");
            }
        }
        
        // 3. Afficher les regroupements par nom de rôle
        Map<String, Integer> roleCounts = new HashMap<>();
        for (Role role : allRoles) {
            String roleName = role.getName();
            List<User> usersWithThisRole = userRepository.findByRoleId(role.getId());
            roleCounts.put(roleName, roleCounts.getOrDefault(roleName, 0) + usersWithThisRole.size());
        }
        
        System.out.println("\n📈 Résumé par nom de rôle:");
        for (Map.Entry<String, Integer> entry : roleCounts.entrySet()) {
            System.out.println("   " + entry.getKey() + ": " + entry.getValue() + " utilisateurs au total");
        }
        
        System.out.println("✅ Les duplications de rôles sont conservées - chaque utilisateur garde son rôle spécifique!");
    }
}
