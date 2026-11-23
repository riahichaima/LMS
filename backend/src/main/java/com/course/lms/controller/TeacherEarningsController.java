package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.TeacherEarningsView;
import com.course.lms.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// « Mes revenus » : l'enseignant n'accède qu'à ses propres ventes (identifié par son token, aucun id en paramètre)
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherEarningsController {

    @Autowired
    private FinanceService financeService;

    @GetMapping("/earnings")
    public ResponseEntity<TeacherEarningsView> earnings(Authentication auth) {
        return ResponseEntity.ok(financeService.teacherEarnings(auth.getName()));
    }
}
