package com.course.lms.controller;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.LoginRequest;
import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.dto.UserWithRoleDetails;
import com.course.lms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class UserController {
    
    private final AuthService authService;
    
    public UserController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        // Forcer le rôle ADMIN
        request.setRole("ADMIN");
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginAdmin(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }
    
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getCurrentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        return ResponseEntity.ok(currentUser);
    }
    
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserWithRoleDetails>> getAllUsers() {
        List<UserWithRoleDetails> users = authService.getAllUsersWithRoleDetails();
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = authService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateAdminInfo(@Valid @RequestBody RegisterRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        
        // Mettre à jour l'utilisateur actuel
        UserResponse updatedUser = authService.updateUser(currentUser.getId(), request);
        return ResponseEntity.ok(updatedUser);
    }
    
    @DeleteMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdminAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        
        authService.deleteUser(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody RegisterRequest request) {
        UserResponse updatedUser = authService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        authService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/teacher")
class TeacherController {
    
    private final AuthService authService;
    
    public TeacherController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerTeacher(@Valid @RequestBody RegisterRequest request) {
        // Forcer le rôle TEACHER
        request.setRole("TEACHER");
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginTeacher(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }
    
    @GetMapping("/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<UserResponse> getCurrentTeacher() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        return ResponseEntity.ok(currentUser);
    }
    
    @PutMapping("/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<UserResponse> updateTeacherInfo(@Valid @RequestBody RegisterRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        
        // Mettre à jour l'utilisateur actuel
        UserResponse updatedUser = authService.updateUser(currentUser.getId(), request);
        return ResponseEntity.ok(updatedUser);
    }
    
    @DeleteMapping("/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteTeacherAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        
        authService.deleteUser(currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/student")
class StudentController {
    
    private final AuthService authService;
    
    public StudentController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerStudent(@Valid @RequestBody RegisterRequest request) {
        // Forcer le rôle STUDENT
        request.setRole("STUDENT");
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginStudent(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }
    
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<UserResponse> getCurrentStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        return ResponseEntity.ok(currentUser);
    }
    
    @PutMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<UserResponse> updateStudentInfo(@Valid @RequestBody RegisterRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        
        // Mettre à jour l'utilisateur actuel
        UserResponse updatedUser = authService.updateUser(currentUser.getId(), request);
        return ResponseEntity.ok(updatedUser);
    }
    
    @DeleteMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> deleteStudentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserResponse currentUser = authService.getUserByEmail(email);
        
        authService.deleteUser(currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
