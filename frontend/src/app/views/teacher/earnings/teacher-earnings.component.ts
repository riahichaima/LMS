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
import {
  FinanceService, PAYOUT_METHODS, PayoutMethod, TeacherEarningsView, WithdrawalStatus, WithdrawalView, payoutLabel
} from '../../../services/finance.service';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
// « My Earnings » : ventes, gains (60 %), soldes du portefeuille virtuel et demandes de retrait
@Component({
  selector: 'app-teacher-earnings',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RowComponent, ColComponent, CardComponent, CardHeaderComponent, CardBodyComponent,
    TableDirective, BadgeComponent, ButtonDirective, AlertComponent, SpinnerComponent, FormControlDirective, IconDirective,
    PageHeaderComponent,
    StatCardComponent
  ],
  templateUrl: './teacher-earnings.component.html',
  styles: [`
    .withdrawal-steps { display: flex; gap: .25rem; list-style: none; padding: 0; margin: 0 0 .35rem; }
    .withdrawal-steps li { flex: 1; display: flex; gap: .4rem; align-items: flex-start; font-size: .8rem; line-height: 1.2; color: var(--cui-secondary-color); }
    .withdrawal-steps .dot { flex: none; width: 1.2rem; height: 1.2rem; border-radius: 50%; border: 2px solid var(--cui-border-color);
      display: inline-flex; align-items: center; justify-content: center; font-size: .7rem; }
    .withdrawal-steps li.done { color: var(--cui-body-color); }
    .withdrawal-steps li.done .dot { background: var(--cui-success); border-color: var(--cui-success); color: #fff; }
    .withdrawal-steps li.current .dot { border-color: var(--cui-info); box-shadow: 0 0 0 3px var(--cui-info-bg-subtle); }
    .withdrawal-steps li.current { color: var(--cui-body-color); font-weight: 600; }
  `]
})
export class TeacherEarningsComponent implements OnInit {
  data = signal<TeacherEarningsView | null>(null);
  loading = signal(false);
  submitting = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  withdrawalAmount: number | null = null;
  // Compte de réception (repris de la dernière demande)
  payoutMethod: PayoutMethod = 'BANK';
  payoutAccount = '';
  readonly payoutMethods = PAYOUT_METHODS;
  readonly payoutLabel = payoutLabel;

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
        const last = d.withdrawals.find(w => w.payoutMethod && w.payoutAccount);
        if (last && !this.payoutAccount) {
          this.payoutMethod = last.payoutMethod!;
          this.payoutAccount = last.payoutAccount!;
        }
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
    if (!this.payoutAccount.trim()) {
      this.errorMessage.set('Indiquez le compte sur lequel recevoir l\'argent.');
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.financeService.requestWithdrawal(this.withdrawalAmount, this.payoutMethod, this.payoutAccount.trim()).subscribe({
      next: (w) => {
        this.submitting.set(false);
        this.withdrawalAmount = null;
        this.successMessage.set(`Demande de retrait #${w.id} envoyée : l'administration va la valider, puis le super administrateur effectuera le transfert.`);
        this.load();
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(err.error?.message || 'La demande de retrait a échoué.');
      }
    });
  }

  statusLabel(s: WithdrawalStatus): string {
    return { PENDING: 'En attente de validation', APPROVED: 'Approuvée — transfert en cours', REJECTED: 'Refusée', PAID: 'Argent transféré' }[s];
  }

  get currentMethod() {
    return PAYOUT_METHODS.find(m => m.value === this.payoutMethod)!;
  }

  /** Étapes : 1 demandée, 2 approuvée par l'administration, 3 transférée par le super administrateur */
  steps(w: WithdrawalView): { label: string; date?: string | null; done: boolean; current: boolean }[] {
    const approved = w.status === 'APPROVED' || w.status === 'PAID';
    return [
      { label: 'Demandée', date: w.requestedAt, done: true, current: false },
      { label: w.status === 'REJECTED' ? 'Refusée' : 'Approuvée', date: w.reviewedAt, done: approved, current: w.status === 'PENDING' },
      { label: 'Transférée', date: w.transferredAt, done: w.status === 'PAID', current: w.status === 'APPROVED' }
    ];
  }

  statusColor(s: WithdrawalStatus): string {
    return { PENDING: 'warning', APPROVED: 'info', REJECTED: 'danger', PAID: 'success' }[s];
  }
}
