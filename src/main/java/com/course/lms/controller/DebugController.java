package com.course.lms.controller;

import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/debug")
public class DebugController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;

    @GetMapping("/database-structure")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDatabaseStructure() {
        Map<String, Object> result = new HashMap<>();
        
        // 1. Afficher tous les rôles
        List<Role> allRoles = roleRepository.findAll();
        Map<String, Object> rolesInfo = new HashMap<>();
        rolesInfo.put("totalRoles", allRoles.size());
        rolesInfo.put("roles", allRoles);
        
        // 2. Afficher tous les utilisateurs avec leurs rôles
        List<User> allUsers = userRepository.findAll();
        Map<String, Object> usersInfo = new HashMap<>();
        usersInfo.put("totalUsers", allUsers.size());
        
        // Créer une liste détaillée des utilisateurs avec leurs rôles
        java.util.List<Map<String, Object>> userDetails = new java.util.ArrayList<>();
        for (User user : allUsers) {
            Map<String, Object> userDetail = new HashMap<>();
            userDetail.put("userId", user.getId());
            userDetail.put("userName", user.getName());
            userDetail.put("userEmail", user.getEmail());
            userDetail.put("roleId", user.getRole().getId());
            userDetail.put("roleName", user.getRole().getName());
            userDetails.add(userDetail);
        }
        usersInfo.put("users", userDetails);
        
        // 3. Grouper par rôle pour voir la distribution
        Map<String, java.util.List<Map<String, Object>>> usersByRole = new HashMap<>();
        for (Map<String, Object> user : userDetails) {
            String roleName = (String) user.get("roleName");
            usersByRole.computeIfAbsent(roleName, k -> new java.util.ArrayList<>()).add(user);
        }
        
        // 4. Compter les doublons de rôles
        Map<String, Integer> roleCount = new HashMap<>();
        for (Role role : allRoles) {
            roleCount.put(role.getName(), roleCount.getOrDefault(role.getName(), 0) + 1);
        }
        
        Map<String, Object> duplicateRoles = new HashMap<>();
        for (Map.Entry<String, Integer> entry : roleCount.entrySet()) {
            if (entry.getValue() > 1) {
                duplicateRoles.put(entry.getKey(), entry.getValue());
            }
        }
        
        result.put("roles", rolesInfo);
        result.put("users", usersInfo);
        result.put("usersByRole", usersByRole);
        result.put("duplicateRoles", duplicateRoles);
        result.put("summary", Map.of(
            "totalRoles", allRoles.size(),
            "uniqueRoles", roleCount.size(),
            "duplicateRoleTypes", duplicateRoles.size(),
            "totalUsers", allUsers.size()
        ));
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/role-details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRoleDetails() {
        List<Role> allRoles = roleRepository.findAll();
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Détail de chaque rôle dans la table roles");
        
        for (Role role : allRoles) {
            List<User> usersWithThisRole = userRepository.findByRoleId(role.getId());
            
            Map<String, Object> roleInfo = new HashMap<>();
            roleInfo.put("roleId", role.getId());
            roleInfo.put("roleName", role.getName());
            roleInfo.put("userCount", usersWithThisRole.size());
            roleInfo.put("users", usersWithThisRole.stream()
                .map(user -> Map.of(
                    "userId", user.getId(),
                    "userName", user.getName(),
                    "userEmail", user.getEmail()
                ))
                .toList());
            
            result.put("role_" + role.getId() + "_" + role.getName(), roleInfo);
        }
        
        return ResponseEntity.ok(result);
    }
}
