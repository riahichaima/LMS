package com.course.lms.service;

import com.course.lms.dto.RegisterRequest;
import com.course.lms.dto.UserResponse;
import com.course.lms.model.Role;
import com.course.lms.model.User;
import com.course.lms.repository.RoleRepository;
import com.course.lms.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseGet(() -> {
                    Role role = new Role("STUDENT");
                    return roleRepository.save(role);
                });
        
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(encodedPassword);
        user.setRole(studentRole);
        user.setEnabled(true);
        
        User savedUser = userRepository.save(user);
        
        UserResponse response = new UserResponse();
        response.setId(savedUser.getId());
        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setRole(savedUser.getRole().getName());
        return response;
    }
}
