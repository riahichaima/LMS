package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.PlatformWalletView;
import com.course.lms.dto.FinanceDtos.TransactionView;
import com.course.lms.dto.FinanceDtos.WithdrawalView;
import com.course.lms.repository.UserRepository;
import com.course.lms.service.FinanceService;
import com.course.lms.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// « Platform Financial Control » : portefeuille de la plateforme et transferts SIMULÉS vers les enseignants
@RestController
@RequestMapping("/api/super-admin/finance")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminFinanceController {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/platform-wallet")
    public ResponseEntity<PlatformWalletView> platformWallet() {
        return ResponseEntity.ok(financeService.platformWallet());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionView>> transactions() {
        return ResponseEntity.ok(financeService.transactions());
    }

    @GetMapping("/transfers")
    public ResponseEntity<List<WithdrawalView>> transfers() {
        return ResponseEntity.ok(financeService.transfers());
    }

    // Transfert simulé d'un retrait approuvé : aucune opération bancaire réelle
    @PostMapping("/transfers/{withdrawalId}/simulate")
    public ResponseEntity<WithdrawalView> simulateTransfer(@PathVariable Long withdrawalId, Authentication auth) {
        return ResponseEntity.ok(WithdrawalView.of(
                walletService.simulateTransfer(withdrawalId, userRepository.findByEmail(auth.getName()).orElseThrow())));
    }
}
