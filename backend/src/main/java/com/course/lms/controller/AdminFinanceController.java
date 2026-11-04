package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.*;
import com.course.lms.model.User;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.FinanceService;
import com.course.lms.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// « Financial Management » de l'administrateur (le super administrateur y a aussi accès)
@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceController {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

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

    @GetMapping("/withdrawals")
    public ResponseEntity<List<WithdrawalView>> withdrawals() {
        return ResponseEntity.ok(financeService.withdrawals());
    }

    @PutMapping("/withdrawals/{id}/approve")
    public ResponseEntity<WithdrawalView> approve(@PathVariable Long id, @RequestBody(required = false) ReviewBody body,
                                                  Authentication auth) {
        return ResponseEntity.ok(WithdrawalView.of(
                walletService.approveWithdrawal(id, currentUser(auth), body != null ? body.note() : null)));
    }

    @PutMapping("/withdrawals/{id}/reject")
    public ResponseEntity<WithdrawalView> reject(@PathVariable Long id, @RequestBody(required = false) ReviewBody body,
                                                 Authentication auth) {
        return ResponseEntity.ok(WithdrawalView.of(
                walletService.rejectWithdrawal(id, currentUser(auth), body != null ? body.note() : null)));
    }

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}
