package com.course.lms.controller;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.ForgotPasswordRequest;
import com.course.lms.dto.PasswordResetRequest;
import com.course.lms.dto.LoginRequest;
import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.exception.AdminApprovalException;
import com.course.lms.exception.EmailNotVerifiedException;
import com.course.lms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(authService.getUserByEmail(email));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /admin/register - Force la création d'un compte ADMIN
     */
    @PostMapping("/admin/register")
    public ResponseEntity<UserResponse> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        request.setRole("ADMIN");
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /teacher/register - Force la création d'un compte TEACHER
     */
    @PostMapping("/teacher/register")
    public ResponseEntity<UserResponse> registerTeacher(@Valid @RequestBody RegisterRequest request) {
        request.setRole("TEACHER");
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /student/register - Force la création d'un compte STUDENT
     */
    @PostMapping("/student/register")
    public ResponseEntity<UserResponse> registerStudent(@Valid @RequestBody RegisterRequest request) {
        request.setRole("STUDENT");
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
    
    @GetMapping("/auth/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email vérifié avec succès ! Vous pouvez maintenant vous connecter."));
    }

    @PostMapping("/auth/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.resendVerificationEmail(request.getEmail());
        } catch (RuntimeException e) {
            // Même réponse que l'email existe ou non (pas de fuite d'information)
        }
        return ResponseEntity.ok(Map.of("message", "Si ce compte existe et n'est pas encore vérifié, un nouvel email de vérification a été envoyé."));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request.getEmail());
        } catch (RuntimeException e) {
            // Même réponse que l'email existe ou non (pas de fuite d'information)
        }
        return ResponseEntity.ok(Map.of("message", "Un e-mail de réinitialisation a été envoyé si l'adresse existe."));
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès ! Vous pouvez maintenant vous connecter."));
    }

    // Réponses d'erreur lisibles par le frontend : { "message": "..." }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Données invalides");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Email ou mot de passe incorrect"));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleNotVerified(EmailNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage(), "code", "EMAIL_NOT_VERIFIED"));
    }

    @ExceptionHandler(AdminApprovalException.class)
    public ResponseEntity<Map<String, String>> handleAdminApproval(AdminApprovalException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage(), "code", ex.getCode()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Compte désactivé, contactez un administrateur"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        HttpStatus status = "Email already exists".equals(ex.getMessage()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        String message = status == HttpStatus.CONFLICT ? "Cet email est déjà utilisé"
                : (ex.getMessage() != null ? ex.getMessage() : "Erreur inattendue");
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
