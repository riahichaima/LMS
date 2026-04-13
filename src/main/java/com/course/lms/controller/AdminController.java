package com.course.lms.controller;

import com.course.lms.dto.UserResponse;
import com.course.lms.dto.UserWithRoleDetails;
import com.course.lms.service.AuthService;
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
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AuthService authService;

    @GetMapping("/roles-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRolesSummary() {
        List<UserWithRoleDetails> users = authService.getAllUsersWithRoleDetails();
        
        Map<String, Object> summary = new HashMap<>();
        
        // Grouper par rôle
        Map<String, List<UserWithRoleDetails>> usersByRole = new HashMap<>();
        usersByRole.put("STUDENT", users.stream().filter(u -> "STUDENT".equals(u.getRole().getRoleName())).toList());
        usersByRole.put("TEACHER", users.stream().filter(u -> "TEACHER".equals(u.getRole().getRoleName())).toList());
        usersByRole.put("ADMIN", users.stream().filter(u -> "ADMIN".equals(u.getRole().getRoleName())).toList());
        
        summary.put("usersByRole", usersByRole);
        summary.put("totalUsers", users.size());
        
        return ResponseEntity.ok(summary);
    }
}
