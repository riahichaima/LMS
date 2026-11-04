package com.course.lms.controller;

import com.course.lms.dto.ComplaintDtos.*;
import com.course.lms.service.ComplaintService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Réclamations : envoi par les étudiants et enseignants, traitement par l'administration. */
@RestController
@RequestMapping("/api/complaints")
@PreAuthorize("isAuthenticated()")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT') or hasRole('TEACHER')")
    public ComplaintView create(@RequestBody ComplaintRequest request, Authentication auth) {
        return complaintService.create(auth.getName(), request);
    }

    @GetMapping("/mine")
    public List<ComplaintView> mine(Authentication auth) {
        return complaintService.mine(auth.getName());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ComplaintView> all() {
        return complaintService.all();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintStats stats() {
        return complaintService.stats();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintView handle(@PathVariable Long id, @RequestBody ComplaintUpdate update, Authentication auth) {
        return complaintService.handle(id, auth.getName(), update);
    }
}
