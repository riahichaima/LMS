package com.course.lms.service;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.LoginRequest;
import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.dto.UserWithRoleDetails;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

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

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        String roleName = (request.getRole() != null && !request.getRole().isEmpty()) 
            ? request.getRole() 
            : "STUDENT";

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
        user.setEnabled(false); // Utilisateur non actif par défaut

        User savedUser = userRepository.save(user);

        // Créer le token de vérification
        String tokenString = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(tokenString, savedUser);
        tokenRepository.save(verificationToken);

        // Envoyer l'email
        emailService.sendVerificationEmail(savedUser.getEmail(), tokenString);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole().getName()
        );
    }

    public boolean verifyEmail(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        // On peut optionnellement supprimer le token après usage
        tokenRepository.delete(verificationToken);
        
        return true;
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String tokenString = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(tokenString, user);
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), tokenString);
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName()
        );

        return new AuthResponse(token, userResponse);
    }

    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole().getName()
                ))
                .collect(Collectors.toList());
    }

    // Nouvelle méthode pour voir les détails complets
    public List<UserWithRoleDetails> getAllUsersWithRoleDetails() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> new UserWithRoleDetails(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        new UserWithRoleDetails.RoleDetails(
                                user.getRole().getId(),
                                user.getRole().getName()
                        )
                ))
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName()
        );
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

        return new UserResponse(
                updatedUser.getId(),
                updatedUser.getName(),
                updatedUser.getEmail(),
                updatedUser.getRole().getName()
        );
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

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
}
