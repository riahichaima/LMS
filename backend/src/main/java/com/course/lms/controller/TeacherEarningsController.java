package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.TeacherEarningsView;
import com.course.lms.dto.FinanceDtos.WithdrawalRequestBody;
import com.course.lms.dto.FinanceDtos.WithdrawalView;
import com.course.lms.model.User;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.FinanceService;
import com.course.lms.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// « My Earnings » : l'enseignant n'accède qu'à son propre portefeuille (identifié par son token, aucun id en paramètre)
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherEarningsController {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/earnings")
    public ResponseEntity<TeacherEarningsView> earnings(Authentication auth) {
        return ResponseEntity.ok(financeService.teacherEarnings(auth.getName()));
    }

    // Demande de retrait VIRTUELLE (aucun virement réel)
    @PostMapping("/withdrawals")
    public ResponseEntity<WithdrawalView> requestWithdrawal(@RequestBody WithdrawalRequestBody body, Authentication auth) {
        User teacher = userRepository.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WithdrawalView.of(walletService.requestWithdrawal(teacher, body.amount(), body.payoutMethod(), body.payoutAccount())));
    }
}
