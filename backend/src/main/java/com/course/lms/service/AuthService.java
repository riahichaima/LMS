package com.course.lms.service;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.LoginRequest;
import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.dto.UserWithRoleDetails;
import com.course.lms.exception.AdminApprovalException;
import com.course.lms.exception.EmailNotVerifiedException;
import com.course.lms.model.ApprovalStatus;
import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.model.VerificationToken;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import com.course.lms.repository.VerificationTokenRepository;
import com.course.lms.model.PasswordResetToken;
import com.course.lms.repository.PasswordResetTokenRepository;
import com.course.lms.security.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailService emailService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        String roleName = (request.getRole() != null && !request.getRole().isEmpty()) 
            ? request.getRole() 
            : "STUDENT";
        if ("SUPER_ADMIN".equals(roleName)) {
            throw new RuntimeException("Le rôle super administrateur ne peut pas être choisi à l'inscription");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role(roleName);
                    return roleRepository.save(newRole);
                });

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setEnabled(false); // Activé après vérification de l'email
        if ("ADMIN".equals(roleName)) {
            // Un compte administrateur doit en plus être validé par le super administrateur
            user.setApprovalStatus(ApprovalStatus.PENDING);
        }

        User savedUser = userRepository.save(user);
        if (ApprovalStatus.PENDING.equals(savedUser.getApprovalStatus())) {
            notificationService.notifyRole("SUPER_ADMIN", "Nouvelle demande de compte admin",
                    savedUser.getName() + " (" + savedUser.getEmail() + ") demande un compte administrateur.",
                    "ADMIN_REQUEST", savedUser.getId());
        }

        if ("STUDENT".equals(roleName) || "TEACHER".equals(roleName)) {
            notificationService.notifyStaff("👤 Nouvel utilisateur : " + savedUser.getName(),
                    savedUser.getName() + " (" + savedUser.getEmail() + ") a créé un compte " + ("TEACHER".equals(roleName) ? "enseignant" : "étudiant") + ".",
                    "ADMIN_USER", savedUser.getId());
        }
        sendVerificationToken(savedUser);

        UserResponse response = new UserResponse();
        response.setId(savedUser.getId());
        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setRole(savedUser.getRole().getName());
        return response;
    }

    // Un seul token par utilisateur (contrainte OneToOne) : on remplace l'ancien
    private void sendVerificationToken(User user) {
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.flush();
        String tokenString = UUID.randomUUID().toString();
        tokenRepository.save(new VerificationToken(tokenString, user));
        emailService.sendVerificationEmail(user.getEmail(), tokenString);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.isEnabled()) {
            throw new RuntimeException("Ce compte est déjà vérifié");
        }
        sendVerificationToken(user);
    }

    public boolean verifyEmail(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Lien invalide ou déjà utilisé"));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Ce lien a expiré, demandez un nouvel email de vérification");
        }

        User user = verificationToken.getUser();
        boolean firstActivation = !user.isEnabled();
        user.setEnabled(true);
        userRepository.save(user);
        if (firstActivation && user.getRole() != null && "TEACHER".equals(user.getRole().getName())) {
            notificationService.notifyRole("STUDENT", "🎓 Nouveau formateur : " + user.getName(),
                    user.getName() + " rejoint la plateforme. Découvrez bientôt ses cours !", "NEW_TEACHER", user.getId());
        }

        // On peut optionnellement supprimer le token après usage
        tokenRepository.delete(verificationToken);
        
        return true;
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        passwordResetTokenRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.flush();
        String tokenString = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(tokenString, user);
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), tokenString);
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Lien invalide ou déjà utilisé"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Ce lien a expiré, veuillez refaire une demande");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }

    /**
     * Identité du compte pour la limite des essais : l'e-mail tel qu'il est enregistré. La base compare les
     * e-mails sans tenir compte des majuscules ni des accents : toutes les façons d'écrire un même compte
     * partagent ainsi un seul compteur. E-mail inconnu : l'adresse saisie.
     */
    public String accountKey(String email) {
        if (email == null || email.isBlank()) return "";
        return userRepository.findByEmail(email.trim()).map(User::getEmail).orElse(email);
    }

    public AuthResponse login(LoginRequest request) {
        // Compte non vérifié : un token de vérification est encore en attente
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            if (!u.isEnabled() && tokenRepository.existsByUserId(u.getId())
                    && passwordEncoder.matches(request.getPassword(), u.getPassword())) {
                throw new EmailNotVerifiedException();
            }
        });

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            systemLogService.record(com.course.lms.model.SystemLog.SECURITY, "AUTH", "Échec de connexion",
                    "Identifiants invalides ou compte désactivé pour " + request.getEmail(), request.getEmail());
            throw e;
        }

        // Compte administrateur pas encore validé par le super administrateur
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            if (ApprovalStatus.PENDING.equals(u.getApprovalStatus())) {
                throw new AdminApprovalException("ADMIN_PENDING",
                        "Votre compte administrateur est en attente de validation par le super administrateur.");
            }
        });

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        systemLogService.record(com.course.lms.model.SystemLog.INFO, "AUTH", "Connexion",
                user.getName() + " (" + user.getRole().getName() + ") s'est connecté", user.getEmail());

        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setName(user.getName());
        userResponse.setEmail(user.getEmail());
        userResponse.setRole(user.getRole().getName());

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken(token);
        authResponse.setUser(userResponse);
        return authResponse;
    }

    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> {
                    UserResponse response = new UserResponse();
                    response.setId(user.getId());
                    response.setName(user.getName());
                    response.setEmail(user.getEmail());
                    response.setRole(user.getRole().getName());
                    return response;
                })
                .collect(Collectors.toList());
    }

    // Nouvelle méthode pour voir les détails complets
    public List<UserWithRoleDetails> getAllUsersWithRoleDetails() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> {
                    UserWithRoleDetails details = new UserWithRoleDetails();
                    details.setId(user.getId());
                    details.setName(user.getName());
                    details.setEmail(user.getEmail());
                    details.setEnabled(user.isEnabled());
                    details.setApprovalStatus(user.getApprovalStatus());
                    
                    UserWithRoleDetails.RoleDetails roleDetails = new UserWithRoleDetails.RoleDetails();
                    roleDetails.setRoleId(user.getRole().getId());
                    roleDetails.setRoleName(user.getRole().getName());
                    details.setRole(roleDetails);
                    
                    return details;
                })
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().getName());
        return response;
    }

    public UserResponse updateUser(Long id, RegisterRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        // Vérifier si l'email est déjà utilisé par un autre utilisateur
        if (!user.getEmail().equals(request.getEmail()) && 
            userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Mettre à jour le rôle si spécifié
        if (request.getRole() != null && !request.getRole().isEmpty()) {
            Role newRole = roleRepository.findByName(request.getRole())
                    .orElseGet(() -> {
                        Role role = new Role(request.getRole());
                        return roleRepository.save(role);
                    });
            user.setRole(newRole);
        }

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        // Mettre à jour le mot de passe seulement si fourni
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(user);

        UserResponse response = new UserResponse();
        response.setId(updatedUser.getId());
        response.setName(updatedUser.getName());
        response.setEmail(updatedUser.getEmail());
        response.setRole(updatedUser.getRole().getName());
        return response;
    }

    // Méthode pour trouver un utilisateur par email
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName()
        );
    }

    @Transactional
    public void deleteUser(Long id) {
        tokenRepository.deleteByUserId(id);
        passwordResetTokenRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    public UserResponse setUserEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        boolean activation = enabled && !user.isEnabled();
        user.setEnabled(enabled);
        User saved = userRepository.save(user);
        if (activation) {
            // Activation par un administrateur : le lien de vérification éventuel n'est plus utile,
            // la personne est prévenue par email et les étudiants découvrent le nouveau formateur
            tokenRepository.deleteByUserId(saved.getId());
            emailService.sendAccountActivatedEmail(saved.getEmail(), saved.getName());
            if (saved.getRole() != null && "TEACHER".equals(saved.getRole().getName())) {
                notificationService.notifyRole("STUDENT", "🎓 Nouveau formateur : " + saved.getName(),
                        saved.getName() + " rejoint la plateforme. Découvrez bientôt ses cours !", "NEW_TEACHER", saved.getId());
            }
        }

        UserResponse response = new UserResponse();
        response.setId(saved.getId());
        response.setName(saved.getName());
        response.setEmail(saved.getEmail());
        response.setRole(saved.getRole().getName());
        return response;
    }
}
