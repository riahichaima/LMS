package com.course.lms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/database")
public class DatabaseViewController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/roles-with-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRolesWithUsers() {
        String sql = """
            SELECT 
                r.id as role_id,
                r.name as role_name,
                u.id as user_id,
                u.name as user_name,
                u.email as user_email
            FROM roles r
            LEFT JOIN users u ON r.id = u.role_id
            ORDER BY r.name, u.id
            """;
        
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/all-users-roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllUsersWithRoles() {
        String sql = """
            SELECT 
                u.id as user_id,
                u.name as user_name,
                u.email as user_email,
                r.id as role_id,
                r.name as role_name
            FROM users u
            LEFT JOIN roles r ON u.role_id = r.id
            ORDER BY r.name, u.id
            """;
        
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/role-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRoleSummary() {
        String sql = """
            SELECT 
                r.id as role_id,
                r.name as role_name,
                COUNT(u.id) as user_count,
                GROUP_CONCAT(u.id ORDER BY u.id) as user_ids,
                GROUP_CONCAT(u.name ORDER BY u.id) as user_names
            FROM roles r
            LEFT JOIN users u ON r.id = u.role_id
            GROUP BY r.id, r.name
            ORDER BY r.name
            """;
        
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }
}
