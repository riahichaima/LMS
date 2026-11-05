package com.course.lms.service;

import com.course.lms.dto.AuthResponse;
import com.course.lms.exception.AdminApprovalException;
import com.course.lms.exception.GoogleAuthException;
import com.course.lms.model.ApprovalStatus;
import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.repository.VerificationTokenRepository;
import com.course.lms.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Connexion / inscription Google : vérification du jeton et règles de création ou de connexion du compte. */
class GoogleAuthServiceTest {

    private static final String CLIENT_ID = "123-test.apps.googleusercontent.com";

    private UserRepository users;
    private RoleRepository roles;
    private VerificationTokenRepository tokens;
    private NotificationService notifications;
    private Jwt nextToken;
    private RuntimeException nextError;
    private GoogleAuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        tokens = mock(VerificationTokenRepository.class);
        notifications = mock(NotificationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService details = mock(UserDetailsService.class);

        when(encoder.encode(anyString())).thenReturn("hash");
        when(jwtUtil.generateToken(any())).thenReturn("jwt-lms");
        when(details.loadUserByUsername(anyString())).thenReturn(mock(UserDetails.class));
        when(roles.findByName(anyString())).thenAnswer(inv -> Optional.of(new Role(inv.getArgument(0))));
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(99L);
            return u;
        });
        service = newService(CLIENT_ID, encoder, jwtUtil, details);
        nextToken = googleToken("nadia.ben@gmail.com", true);
    }

    private GoogleAuthService newService(String clientId, PasswordEncoder encoder, JwtUtil jwtUtil, UserDetailsService details) {
        return new GoogleAuthService(clientId, users, roles, tokens, encoder, jwtUtil, details, notifications, mock(SystemLogService.class)) {
            @Override
            protected Jwt decode(String credential) {
                if (nextError != null) throw nextError;
                return nextToken;
            }
        };
    }

    private static Jwt googleToken(String email, Object verified) {
        return Jwt.withTokenValue("google-id-token").header("alg", "RS256")
                .claim("iss", "https://accounts.google.com").audience(List.of(CLIENT_ID))
                .claim("email", email).claim("email_verified", verified)
                .claim("name", "Nadia Ben Salah").claim("given_name", "Nadia").claim("family_name", "Ben Salah")
                .issuedAt(Instant.now().minusSeconds(10)).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    private static User existing(String roleName, boolean enabled) {
        User u = new User("Sami", "sami@gmail.com", "hash", new Role(roleName));
        u.setId(7L);
        u.setEnabled(enabled);
        return u;
    }

    private static void assertGoogleError(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GoogleAuthException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void refusedWhenGoogleIsNotConfigured() {
        GoogleAuthService off = newService("", mock(PasswordEncoder.class), mock(JwtUtil.class), mock(UserDetailsService.class));
        assertThat(off.isEnabled()).isFalse();
        assertGoogleError(() -> off.authenticate("x", "STUDENT"), HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_DISABLED");
    }

    @Test
    void refusesInvalidOrForgedToken() {
        nextError = new BadJwtException("signature invalide");
        assertGoogleError(() -> service.authenticate("forged", "STUDENT"), HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID");
        verify(users, never()).save(any());
    }

    @Test
    void refusesUnverifiedGoogleEmail() {
        nextToken = googleToken("nadia.ben@gmail.com", false);
        assertGoogleError(() -> service.authenticate("t", "STUDENT"), HttpStatus.UNAUTHORIZED, "GOOGLE_EMAIL_NOT_VERIFIED");
    }

    @Test
    void loginPageWithoutAccountAsksToRegister() {
        assertGoogleError(() -> service.authenticate("t", null), HttpStatus.NOT_FOUND, "GOOGLE_NO_ACCOUNT");
        verify(users, never()).save(any());
    }

    @Test
    void googleSignupCreatesDisabledAccountWaitingForAdmin() {
        assertThatThrownBy(() -> service.authenticate("t", "STUDENT"))
                .isInstanceOfSatisfying(AdminApprovalException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_PENDING_ACTIVATION"));

        verify(users).save(argThat(u -> !u.isEnabled() && "nadia.ben@gmail.com".equals(u.getEmail())
                && "Nadia".equals(u.getFirstName()) && "Ben Salah".equals(u.getLastName()) && "hash".equals(u.getPassword())));
        verify(notifications).notifyStaff(anyString(), anyString(), eq("ADMIN_USER"), eq(99L));
        verify(notifications, never()).notifyRole(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void teacherIsAnnouncedToStudentsOnlyAfterActivation() {
        assertThatThrownBy(() -> service.authenticate("t", "teacher")).isInstanceOf(AdminApprovalException.class);
        verify(notifications, never()).notifyRole(eq("STUDENT"), anyString(), anyString(), eq("NEW_TEACHER"), anyLong());
    }

    @Test
    void adminSignupWaitsForSuperAdmin() {
        assertThatThrownBy(() -> service.authenticate("t", "ADMIN")).isInstanceOf(AdminApprovalException.class);
        verify(users).save(argThat(u -> ApprovalStatus.PENDING.equals(u.getApprovalStatus())));
        verify(notifications).notifyRole(eq("SUPER_ADMIN"), anyString(), anyString(), eq("ADMIN_REQUEST"), eq(99L));
    }

    @Test
    void cannotChooseSuperAdminRole() {
        assertGoogleError(() -> service.authenticate("t", "SUPER_ADMIN"), HttpStatus.BAD_REQUEST, "GOOGLE_ROLE_INVALID");
        verify(users, never()).save(any());
    }

    @Test
    void existingAccountSimplyLogsIn() {
        when(users.findByEmail("nadia.ben@gmail.com")).thenReturn(Optional.of(existing("TEACHER", true)));
        AuthResponse auth = service.authenticate("t", "STUDENT"); // le rôle demandé ne change pas un compte existant

        assertThat(auth.getUser().getRole()).isEqualTo("TEACHER");
        verify(users, never()).save(any());
    }

    @Test
    void googleEmailIsMatchedCaseInsensitively() {
        nextToken = googleToken("Nadia.Ben@Gmail.com", "true");
        when(users.findByEmail("nadia.ben@gmail.com")).thenReturn(Optional.of(existing("STUDENT", true)));
        assertThat(service.authenticate("t", null).getUser().getId()).isEqualTo(7L);
    }

    @Test
    void unverifiedPasswordAccountIsActivatedByGoogle() {
        User waiting = existing("STUDENT", false);
        when(users.findByEmail("nadia.ben@gmail.com")).thenReturn(Optional.of(waiting));
        when(tokens.existsByUserId(7L)).thenReturn(true);

        service.authenticate("t", null);

        assertThat(waiting.isEnabled()).isTrue();
        verify(tokens).deleteByUserId(7L);
    }

    @Test
    void googleAccountNotYetActivatedCannotLogIn() {
        when(users.findByEmail("nadia.ben@gmail.com")).thenReturn(Optional.of(existing("STUDENT", false)));
        when(tokens.existsByUserId(7L)).thenReturn(false);
        assertThatThrownBy(() -> service.authenticate("t", null))
                .isInstanceOfSatisfying(AdminApprovalException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_PENDING_ACTIVATION"));
    }

    @Test
    void pendingAdminCannotLogIn() {
        User admin = existing("ADMIN", true);
        admin.setApprovalStatus(ApprovalStatus.PENDING);
        when(users.findByEmail("nadia.ben@gmail.com")).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.authenticate("t", null)).isInstanceOf(AdminApprovalException.class);
    }

    @Test
    void validatorChecksIssuerAudienceAndExpiry() {
        var validator = service.validator();
        assertThat(validator.validate(googleToken("a@gmail.com", true)).hasErrors()).isFalse();

        Jwt otherApp = Jwt.withTokenValue("t").header("alg", "RS256").claims(c -> c.putAll(googleToken("a@gmail.com", true).getClaims()))
                .audience(List.of("autre-application.apps.googleusercontent.com")).build();
        assertThat(validator.validate(otherApp).hasErrors()).isTrue();

        Jwt otherIssuer = Jwt.withTokenValue("t").header("alg", "RS256").claims(c -> c.putAll(googleToken("a@gmail.com", true).getClaims()))
                .claim("iss", "https://evil.example.com").build();
        assertThat(validator.validate(otherIssuer).hasErrors()).isTrue();

        Jwt expired = Jwt.withTokenValue("t").header("alg", "RS256").claims(c -> c.putAll(Map.of("iss", "accounts.google.com", "aud", List.of(CLIENT_ID))))
                .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build();
        assertThat(validator.validate(expired).hasErrors()).isTrue();
    }
}
