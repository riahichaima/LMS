import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  FormControlDirective,
  RowComponent,
  SpinnerComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { FinanceService, TeacherEarningsView, WithdrawalStatus } from '../../../services/finance.service';

// « My Earnings » : ventes, gains (60 %), soldes du portefeuille virtuel et demandes de retrait
@Component({
  selector: 'app-teacher-earnings',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RowComponent, ColComponent, CardComponent, CardHeaderComponent, CardBodyComponent,
    TableDirective, BadgeComponent, ButtonDirective, AlertComponent, SpinnerComponent, FormControlDirective, IconDirective
  ],
  templateUrl: './teacher-earnings.component.html'
})
export class TeacherEarningsComponent implements OnInit {
  data = signal<TeacherEarningsView | null>(null);
  loading = signal(false);
  submitting = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  withdrawalAmount: number | null = null;

  constructor(private financeService: FinanceService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.financeService.getMyEarnings().subscribe({
      next: (d) => {
        this.loading.set(false);
        this.data.set(d);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger vos revenus.');
      }
    });
  }

  requestWithdrawal(): void {
    const d = this.data();
    if (!d || this.withdrawalAmount == null) return;
    if (this.withdrawalAmount < d.minWithdrawal || this.withdrawalAmount > d.availableBalance) {
      this.errorMessage.set(`Le montant doit être compris entre ${d.minWithdrawal} $ et votre solde disponible.`);
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.financeService.requestWithdrawal(this.withdrawalAmount).subscribe({
      next: (w) => {
        this.submitting.set(false);
        this.withdrawalAmount = null;
        this.successMessage.set(`Demande de retrait #${w.id} envoyée : en attente de validation par l'administrateur.`);
        this.load();
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(err.error?.message || 'La demande de retrait a échoué.');
      }
    });
  }

  statusLabel(s: WithdrawalStatus): string {
    return { PENDING: 'En attente', APPROVED: 'Approuvée — transfert à venir', REJECTED: 'Refusée', PAID: 'Transférée' }[s];
  }

  statusColor(s: WithdrawalStatus): string {
    return { PENDING: 'warning', APPROVED: 'info', REJECTED: 'danger', PAID: 'success' }[s];
  }
}
