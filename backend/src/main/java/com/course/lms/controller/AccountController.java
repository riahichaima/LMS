package com.course.lms.controller;

import com.course.lms.service.AccountDeletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mon compte (étudiant / enseignant) :
 *   GET  /api/account/deletion-reasons → raisons proposées selon le rôle
 *   POST /api/account/delete           → { reason, details } : raison envoyée à l'administration, compte supprimé
 */
@RestController
@RequestMapping("/api/account")
@PreAuthorize("hasRole('STUDENT') or hasRole('TEACHER')")
public class AccountController {

    private final AccountDeletionService accountDeletionService;

    public AccountController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    public record DeleteAccountRequest(String reason, String details) {}

    @GetMapping("/deletion-reasons")
    public List<Map<String, String>> reasons(Authentication auth) {
        return AccountDeletionService.asList(accountDeletionService.reasonsFor(auth.getName()));
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody DeleteAccountRequest request, Authentication auth) {
        accountDeletionService.deleteOwnAccount(auth.getName(), request == null ? null : request.reason(),
                request == null ? null : request.details());
        return ResponseEntity.noContent().build();
    }
}
