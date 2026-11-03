import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  ColComponent,
  RowComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { FinanceService, FinanceSummary, TeacherWalletView, WithdrawalStatus, WithdrawalView } from '../../../services/finance.service';
import { PaymentStatus, PaymentView } from '../../../services/payment.service';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
// « Financial Management » : paiements, commissions, revenus des enseignants et retraits (simulés)
@Component({
  selector: 'app-admin-finance',
  standalone: true,
  imports: [
    CommonModule, RowComponent, ColComponent, CardComponent, CardBodyComponent, TableDirective, BadgeComponent,
    ButtonDirective, AlertComponent, IconDirective,
    PageHeaderComponent,
    StatCardComponent
  ],
  templateUrl: './admin-finance.component.html'
})
export class AdminFinanceComponent implements OnInit {
  tab = signal<'payments' | 'commissions' | 'teachers' | 'withdrawals'>('withdrawals');
  summary = signal<FinanceSummary | null>(null);
  payments = signal<PaymentView[]>([]);
  teachers = signal<TeacherWalletView[]>([]);
  withdrawals = signal<WithdrawalView[]>([]);
  loading = signal(false);
  busyId = signal<number | null>(null);
  errorMessage = signal('');
  successMessage = signal('');

  paidPayments = computed(() => this.payments().filter(p => p.status === 'PAID'));
  pendingWithdrawals = computed(() => this.withdrawals().filter(w => w.status === 'PENDING'));

  constructor(private financeService: FinanceService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.financeService.getSummary().subscribe({ next: (s) => this.summary.set(s) });
    this.financeService.getPayments().subscribe({ next: (p) => this.payments.set(p) });
    this.financeService.getTeacherWallets().subscribe({ next: (t) => this.teachers.set(t) });
    this.financeService.getWithdrawals().subscribe({
      next: (w) => {
        this.withdrawals.set(w);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger les données financières.');
      }
    });
  }

  approve(w: WithdrawalView): void {
    this.decide(w, true, null);
  }

  reject(w: WithdrawalView): void {
    const note = prompt(`Motif du refus du retrait #${w.id} (${w.teacherName}) :`, '');
    if (note === null) return;
    this.decide(w, false, note);
  }

  paymentColor(s: PaymentStatus): string {
    return { PAID: 'success', PENDING: 'warning', FAILED: 'danger', EXPIRED: 'secondary', CANCELLED: 'secondary', REFUNDED: 'info' }[s];
  }

  withdrawalLabel(s: WithdrawalStatus): string {
    return { PENDING: 'En attente', APPROVED: 'Approuvée', REJECTED: 'Refusée', PAID: 'Transférée (simulé)' }[s];
  }

  withdrawalColor(s: WithdrawalStatus): string {
    return { PENDING: 'warning', APPROVED: 'info', REJECTED: 'danger', PAID: 'success' }[s];
  }

  private decide(w: WithdrawalView, approved: boolean, note: string | null): void {
    this.busyId.set(w.id);
    this.errorMessage.set('');
    this.successMessage.set('');
    const request = approved ? this.financeService.approveWithdrawal(w.id) : this.financeService.rejectWithdrawal(w.id, note || undefined);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        this.successMessage.set(approved
          ? `Retrait #${w.id} approuvé : le super administrateur peut maintenant simuler le transfert.`
          : `Retrait #${w.id} refusé : le montant est de nouveau disponible pour ${w.teacherName}.`);
        this.load();
      },
      error: (err) => {
        this.busyId.set(null);
        this.errorMessage.set(err.error?.message || 'Décision impossible.');
      }
    });
  }
}
