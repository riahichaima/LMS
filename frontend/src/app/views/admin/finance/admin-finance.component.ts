import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AlertComponent,
  BadgeComponent,
  CardBodyComponent,
  CardComponent,
  ColComponent,
  RowComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { FinanceService, FinanceSummary, TeacherWalletView } from '../../../services/finance.service';
import { PaymentStatus, PaymentView } from '../../../services/payment.service';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
// « Gestion financière » : paiements, commissions de la plateforme (40 %) et revenus des enseignants (60 %), en lecture
@Component({
  selector: 'app-admin-finance',
  standalone: true,
  imports: [
    CommonModule, RowComponent, ColComponent, CardComponent, CardBodyComponent, TableDirective, BadgeComponent,
    AlertComponent, IconDirective,
    PageHeaderComponent,
    StatCardComponent
  ],
  templateUrl: './admin-finance.component.html'
})
export class AdminFinanceComponent implements OnInit {
  tab = signal<'payments' | 'commissions' | 'teachers'>('payments');
  summary = signal<FinanceSummary | null>(null);
  payments = signal<PaymentView[]>([]);
  teachers = signal<TeacherWalletView[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  paidPayments = computed(() => this.payments().filter(p => p.status === 'PAID'));

  constructor(private financeService: FinanceService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.financeService.getSummary().subscribe({ next: (s) => this.summary.set(s) });
    this.financeService.getTeacherWallets().subscribe({ next: (t) => this.teachers.set(t) });
    this.financeService.getPayments().subscribe({
      next: (p) => {
        this.payments.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger les données financières.');
      }
    });
  }

  paymentColor(s: PaymentStatus): string {
    return { PAID: 'success', PENDING: 'warning', FAILED: 'danger', EXPIRED: 'secondary', CANCELLED: 'secondary', REFUNDED: 'info' }[s];
  }
}
