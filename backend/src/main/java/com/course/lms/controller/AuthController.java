package com.course.lms.controller;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.ForgotPasswordRequest;
import com.course.lms.dto.GoogleAuthRequest;
import com.course.lms.dto.PasswordResetRequest;
import com.course.lms.dto.LoginRequest;
import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.exception.AdminApprovalException;
import com.course.lms.exception.EmailNotVerifiedException;
import com.course.lms.exception.GoogleAuthException;
import com.course.lms.model.SystemLog;
import com.course.lms.service.AuthService;
import com.course.lms.service.GoogleAuthService;
import com.course.lms.service.LoginRateLimiter;
import com.course.lms.service.LoginRateLimiter.LoginRateLimitException;
import com.course.lms.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
    private final GoogleAuthService googleAuthService;
    private final LoginRateLimiter loginRateLimiter;
    private final SystemLogService systemLogService;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService,
                          LoginRateLimiter loginRateLimiter, SystemLogService systemLogService) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.loginRateLimiter = loginRateLimiter;
        this.systemLogService = systemLogService;
    }

    /** Connexion ; après trop d'essais refusés (par compte ou par IP), refus 429 pendant quelques minutes. */
    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        String account = authService.accountKey(request.getEmail());
        // L'essai est réservé avant la vérification : tout refus le consomme (mot de passe faux, compte désactivé
        // ou pas encore vérifié…), seule une connexion réussie le rend
        boolean filled = loginRateLimiter.checkLogin(account, ip);
        AuthResponse authResponse;
        try {
            authResponse = authService.login(request);
        } catch (RuntimeException e) {
            if (filled) {
                systemLogService.record(SystemLog.SECURITY, "AUTH", "Connexion bloquée temporairement",
                        "Trop d'essais refusés pour " + account + " ou depuis l'IP " + ip, account);
            }
            throw e;
        }
        loginRateLimiter.loginSucceeded(account, ip);
        return ResponseEntity.ok(authResponse);
    }

    /** Le bouton « Continuer avec Google » n'est affiché que si le serveur a un Client ID Google. */
    @GetMapping("/auth/google/config")
    public ResponseEntity<Map<String, Object>> googleConfig() {
        return ResponseEntity.ok(Map.of("enabled", googleAuthService.isEnabled(), "clientId", googleAuthService.clientId()));
    }

    /** Connexion (page de connexion) ou inscription (page d'inscription, avec le rôle choisi) avec Google. */
    @PostMapping("/auth/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(googleAuthService.authenticate(request.credential(), request.role()));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(authService.getUserByEmail(email));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return registerLimited(request, http);
    }

    /**
     * POST /admin/register - Force la création d'un compte ADMIN
     */
    @PostMapping("/admin/register")
    public ResponseEntity<UserResponse> registerAdmin(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        request.setRole("ADMIN");
        return registerLimited(request, http);
    }

    /**
     * POST /teacher/register - Force la création d'un compte TEACHER
     */
    @PostMapping("/teacher/register")
    public ResponseEntity<UserResponse> registerTeacher(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        request.setRole("TEACHER");
        return registerLimited(request, http);
    }

    /**
     * POST /student/register - Force la création d'un compte STUDENT
     */
    @PostMapping("/student/register")
    public ResponseEntity<UserResponse> registerStudent(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        request.setRole("STUDENT");
        return registerLimited(request, http);
    }

    /** Une inscription envoie un e-mail de vérification : même limite que « mot de passe oublié ». */
    private ResponseEntity<UserResponse> registerLimited(RegisterRequest request, HttpServletRequest http) {
        loginRateLimiter.checkMail(authService.accountKey(request.getEmail()), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/auth/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email vérifié avec succès ! Vous pouvez maintenant vous connecter."));
    }

    @PostMapping("/auth/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ForgotPasswordRequest request,
                                                                  HttpServletRequest http) {
        loginRateLimiter.checkMail(authService.accountKey(request.getEmail()), http.getRemoteAddr());
        try {
            authService.resendVerificationEmail(request.getEmail());
        } catch (RuntimeException e) {
            // Même réponse que l'email existe ou non (pas de fuite d'information)
        }
        return ResponseEntity.ok(Map.of("message", "Si ce compte existe et n'est pas encore vérifié, un nouvel email de vérification a été envoyé."));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                              HttpServletRequest http) {
        loginRateLimiter.checkMail(authService.accountKey(request.getEmail()), http.getRemoteAddr());
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

    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<Map<String, String>> handleTooManyAttempts(LoginRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("message", ex.getMessage(), "code", "TOO_MANY_ATTEMPTS"));
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

    @ExceptionHandler(GoogleAuthException.class)
    public ResponseEntity<Map<String, String>> handleGoogle(GoogleAuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage(), "code", ex.getCode()));
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
