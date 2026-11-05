package com.course.lms.service;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.UserResponse;
import com.course.lms.exception.AdminApprovalException;
import com.course.lms.exception.GoogleAuthException;
import com.course.lms.model.ApprovalStatus;
import com.course.lms.model.Role;
import com.course.lms.model.SystemLog;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.repository.VerificationTokenRepository;
import com.course.lms.security.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Connexion et inscription avec un compte Google (Google Identity Services).
 * Le frontend envoie le jeton d'identité signé par Google ; on vérifie sa signature (clés publiques de Google),
 * son émetteur, son destinataire (notre Client ID), sa date d'expiration et que l'email est vérifié,
 * puis on connecte le compte LMS correspondant ou on le crée. Aucun mot de passe Google n'est vu par le LMS.
 */
@Service
public class GoogleAuthService {

    private static final String GOOGLE_CERTS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final Set<String> SIGNUP_ROLES = Set.of("STUDENT", "TEACHER", "ADMIN");

    private final String clientId;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final NotificationService notificationService;
    private final SystemLogService systemLogService;
    private volatile JwtDecoder decoder;

    public GoogleAuthService(@Value("${app.google.client-id:}") String clientId,
                             UserRepository userRepository, RoleRepository roleRepository,
                             VerificationTokenRepository tokenRepository, PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil, UserDetailsService userDetailsService,
                             NotificationService notificationService, SystemLogService systemLogService) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.notificationService = notificationService;
        this.systemLogService = systemLogService;
    }

    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    /** Client ID public (il est aussi visible dans la page du bouton Google) : le frontend s'en sert pour afficher le bouton. */
    public String clientId() {
        return clientId;
    }

    @Transactional(noRollbackFor = AdminApprovalException.class)
    public AuthResponse authenticate(String credential, String requestedRole) {
        if (!isEnabled()) {
            throw new GoogleAuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_DISABLED",
                    "La connexion avec Google n'est pas configurée sur ce serveur.");
        }
        Jwt token;
        try {
            token = decode(credential);
        } catch (JwtException e) {
            systemLogService.record(SystemLog.SECURITY, "AUTH", "Jeton Google refusé", e.getMessage(), null);
            throw new GoogleAuthException(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID",
                    "Connexion Google invalide ou expirée, veuillez réessayer.");
        }

        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank() || !isTrue(token.getClaims().get("email_verified"))) {
            throw new GoogleAuthException(HttpStatus.UNAUTHORIZED, "GOOGLE_EMAIL_NOT_VERIFIED",
                    "Cette adresse Google n'est pas vérifiée.");
        }
        email = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = createAccount(token, email, requestedRole);
        } else {
            activateIfWaitingForEmail(user);
        }

        if (ApprovalStatus.PENDING.equals(user.getApprovalStatus())) {
            throw new AdminApprovalException("ADMIN_PENDING",
                    "Votre compte administrateur est en attente de validation par le super administrateur.");
        }

        systemLogService.record(SystemLog.INFO, "AUTH", "Connexion (Google)",
                user.getName() + " (" + user.getRole().getName() + ") s'est connecté avec Google", user.getEmail());
        return response(user);
    }

    /** Vérification du jeton : signature RSA de Google, expiration, émetteur et destinataire (notre Client ID). */
    protected Jwt decode(String credential) {
        JwtDecoder current = decoder;
        if (current == null) {
            NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_CERTS).build();
            nimbus.setJwtValidator(validator());
            decoder = current = nimbus;
        }
        return current.decode(credential);
    }

    /** Règles appliquées après la vérification de la signature : expiration, émetteur Google, destinataire = notre Client ID. */
    OAuth2TokenValidator<Jwt> validator() {
        OAuth2TokenValidator<Jwt> issuer = jwt -> GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Émetteur inattendu", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Jeton destiné à une autre application", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience);
    }

    private User createAccount(Jwt token, String email, String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            throw new GoogleAuthException(HttpStatus.NOT_FOUND, "GOOGLE_NO_ACCOUNT",
                    "Aucun compte n'est associé à cette adresse Google. Créez votre compte en choisissant votre rôle.");
        }
        String roleName = requestedRole.trim().toUpperCase(Locale.ROOT);
        if (!SIGNUP_ROLES.contains(roleName)) {
            throw new GoogleAuthException(HttpStatus.BAD_REQUEST, "GOOGLE_ROLE_INVALID", "Rôle non autorisé à l'inscription.");
        }
        Role role = roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(new Role(roleName)));

        String given = token.getClaimAsString("given_name");
        String family = token.getClaimAsString("family_name");
        String name = token.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        }
        if (name.isBlank()) name = email.substring(0, email.indexOf('@'));

        User user = new User();
        user.setName(name);
        user.setFirstName(given);
        user.setLastName(family);
        user.setEmail(email);
        // Pas de mot de passe connu : la personne pourra en définir un avec « Mot de passe oublié »
        user.setPassword(passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()));
        user.setRole(role);
        // Adresse vérifiée par Google, mais le compte doit être activé par un administrateur
        // (étudiant / enseignant) ou validé par le super administrateur (admin)
        user.setEnabled("ADMIN".equals(roleName));
        if ("ADMIN".equals(roleName)) user.setApprovalStatus(ApprovalStatus.PENDING);
        user = userRepository.save(user);

        systemLogService.record(SystemLog.INFO, "AUTH", "Inscription (Google)",
                name + " a créé un compte " + roleName + " avec Google", email);
        if ("ADMIN".equals(roleName)) {
            notificationService.notifyRole("SUPER_ADMIN", "Nouvelle demande de compte admin",
                    name + " (" + email + ") demande un compte administrateur.", "ADMIN_REQUEST", user.getId());
            throw new AdminApprovalException("ADMIN_PENDING",
                    "Votre demande de compte administrateur a été envoyée : le super administrateur doit la valider.");
        }
        notificationService.notifyStaff("👤 Compte à activer : " + name,
                name + " (" + email + ") a créé un compte " + ("TEACHER".equals(roleName) ? "enseignant" : "étudiant")
                        + " avec Google. Activez-le dans Utilisateurs.",
                "ADMIN_USER", user.getId());
        // Les étudiants sont prévenus d'un nouveau formateur seulement à l'activation du compte
        throw new AdminApprovalException("ACCOUNT_PENDING_ACTIVATION",
                "Votre compte a été créé avec Google. Un administrateur doit l'activer avant votre première connexion : vous recevrez un email dès qu'il sera actif.");
    }

    /**
     * Compte créé avec email + mot de passe mais jamais vérifié : Google prouve que l'adresse appartient
     * bien à la personne, on l'active. Un compte désactivé par un administrateur reste bloqué.
     */
    private void activateIfWaitingForEmail(User user) {
        if (user.isEnabled()) return;
        // Pas d'email en attente de vérification : compte créé avec Google et pas encore activé, ou désactivé par l'administration
        if (!tokenRepository.existsByUserId(user.getId())) {
            throw new AdminApprovalException("ACCOUNT_PENDING_ACTIVATION",
                    "Votre compte n'est pas encore activé par l'administration (ou a été désactivé). Réessayez après l'activation.");
        }
        user.setEnabled(true);
        userRepository.save(user);
        tokenRepository.deleteByUserId(user.getId());
        if (user.getRole() != null && "TEACHER".equals(user.getRole().getName())) {
            notificationService.notifyRole("STUDENT", "🎓 Nouveau formateur : " + user.getName(),
                    user.getName() + " rejoint la plateforme. Découvrez bientôt ses cours !", "NEW_TEACHER", user.getId());
        }
    }

    private AuthResponse response(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setName(user.getName());
        userResponse.setEmail(user.getEmail());
        userResponse.setRole(user.getRole().getName());
        AuthResponse auth = new AuthResponse();
        auth.setToken(jwtUtil.generateToken(userDetailsService.loadUserByUsername(user.getEmail())));
        auth.setUser(userResponse);
        return auth;
    }

    private static boolean isTrue(Object claim) {
        return Boolean.TRUE.equals(claim) || "true".equalsIgnoreCase(String.valueOf(claim));
    }
}
