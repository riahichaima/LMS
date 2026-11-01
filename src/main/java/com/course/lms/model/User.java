package com.course.lms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private boolean enabled = false;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // Validation par le super administrateur (comptes ADMIN) : PENDING ou APPROVED
    // (une demande refusée entraîne la suppression du compte).
    // Null pour les comptes qui n'en ont pas besoin (étudiants, enseignants, anciens comptes).
    @Column(name = "approval_status", length = 20)
    private String approvalStatus;

    // Date de création du compte (null pour les comptes créés avant l'ajout de ce champ)
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    // Prénom et nom (le champ « name » reste le nom complet affiché partout)
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    // Photo de profil : nom du fichier dans uploads/avatars (null = initiales)
    @Column(name = "avatar_path", length = 255)
    private String avatarPath;

    @PrePersist
    protected void onCreateUser() {
        if (createdAt == null) createdAt = java.time.LocalDateTime.now();
        splitNameIfNeeded();
    }

    @PreUpdate
    protected void onUpdateUser() {
        splitNameIfNeeded();
    }

    /**
     * Garde prénom / nom cohérents avec le nom complet : si le nom complet a été changé ailleurs
     * (inscription, administrateur), il est découpé au premier espace (prénom = premier mot).
     */
    public void splitNameIfNeeded() {
        if (name == null || name.isBlank()) return;
        String full = name.trim();
        String joined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        if (joined.equals(full)) return;
        int space = full.indexOf(' ');
        firstName = space < 0 ? full : full.substring(0, space);
        lastName = space < 0 ? null : full.substring(space + 1).trim();
    }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
    
    public User(String name, String email, String password, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
}
