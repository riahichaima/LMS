package com.course.lms.controller;

import com.course.lms.dto.FinanceDtos.PlatformWalletView;
import com.course.lms.dto.FinanceDtos.TransactionView;
import com.course.lms.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// « Contrôle financier » : portefeuille de la plateforme (40 % de chaque vente) et historique des mouvements
@RestController
@RequestMapping("/api/super-admin/finance")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminFinanceController {

    @Autowired
    private FinanceService financeService;

    @GetMapping("/platform-wallet")
    public ResponseEntity<PlatformWalletView> platformWallet() {
        return ResponseEntity.ok(financeService.platformWallet());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionView>> transactions() {
        return ResponseEntity.ok(financeService.transactions());
    }
}
