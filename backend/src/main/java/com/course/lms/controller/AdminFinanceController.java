package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.*;
import com.course.lms.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// « Gestion financière » de l'administrateur (le super administrateur y a aussi accès) : ventes et revenus, en lecture
@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceController {

    @Autowired
    private FinanceService financeService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummary> summary() {
        return ResponseEntity.ok(financeService.summary());
    }

    @GetMapping("/payments")
    public ResponseEntity<List<PaymentView>> payments() {
        return ResponseEntity.ok(financeService.payments());
    }

    @GetMapping("/teacher-earnings")
    public ResponseEntity<List<TeacherWalletView>> teacherEarnings() {
        return ResponseEntity.ok(financeService.teacherWallets());
    }
}
