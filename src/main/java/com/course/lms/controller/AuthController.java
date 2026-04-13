package com.course.lms.controller;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.ForgotPasswordRequest;
import com.course.lms.dto.PasswordResetRequest;
import com.course.lms.dto.LoginRequest;
import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }
    
    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok("Email vérifié avec succès ! Vous pouvez maintenant vous connecter.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Erreur : " + e.getMessage());
        }
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request.getEmail());
            return ResponseEntity.ok("Un e-mail de réinitialisation a été envoyé si l'adresse existe.");
        } catch (RuntimeException e) {
            // Indépendamment du succès ou de l'échec pour des raisons de sécurité (évite l'énumération d'emails)
            return ResponseEntity.ok("Un e-mail de réinitialisation a été envoyé si l'adresse existe.");
        }
    }
    
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok("Mot de passe réinitialisé avec succès ! Vous pouvez maintenant vous connecter.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Erreur : " + e.getMessage());
        }
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Debug: afficher l'authentification
        System.out.println("Authentication: " + authentication);
        System.out.println("Principal: " + (authentication != null ? authentication.getName() : "null"));
        System.out.println("Authorities: " + (authentication != null ? authentication.getAuthorities() : "null"));
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String email = authentication.getName();
        System.out.println("Looking for user with email: " + email);
        
        // Récupérer l'utilisateur directement par email
        UserResponse currentUser = authService.getUserByEmail(email);
        
        return ResponseEntity.ok(currentUser);
    }
    
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = authService.getAllUsers();
        return ResponseEntity.ok(users);
    }
}
