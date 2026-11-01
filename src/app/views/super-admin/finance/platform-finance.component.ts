import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { FinanceService, PlatformWalletView, TransactionView, WithdrawalView } from '../../../services/finance.service';

// « Platform Financial Control » : portefeuille de la plateforme, historique et transferts SIMULÉS
@Component({
  selector: 'app-platform-finance',
  standalone: true,
  imports: [
    CommonModule, RowComponent, ColComponent, CardComponent, CardHeaderComponent, CardBodyComponent, TableDirective,
    BadgeComponent, ButtonDirective, AlertComponent, IconDirective
  ],
  templateUrl: './platform-finance.component.html'
})
export class PlatformFinanceComponent implements OnInit {
  wallet = signal<PlatformWalletView | null>(null);
  transfers = signal<WithdrawalView[]>([]);
  transactions = signal<TransactionView[]>([]);
  loading = signal(false);
  busyId = signal<number | null>(null);
  errorMessage = signal('');
  successMessage = signal('');

  toSimulate = computed(() => this.transfers().filter(t => t.status === 'APPROVED'));
  done = computed(() => this.transfers().filter(t => t.status === 'PAID'));

  private readonly txLabels: Record<string, string> = {
    SALE_EARNING: 'Gain de vente (60 %)',
    PLATFORM_COMMISSION: 'Commission (40 %)',
    WITHDRAWAL_HOLD: 'Retrait demandé',
    WITHDRAWAL_RELEASE: 'Retrait refusé',
    WITHDRAWAL_PAYOUT: 'Transfert simulé'
  };

  constructor(private financeService: FinanceService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.financeService.getPlatformWallet().subscribe({ next: (w) => this.wallet.set(w) });
    this.financeService.getTransfers().subscribe({ next: (t) => this.transfers.set(t) });
    this.financeService.getTransactions().subscribe({
      next: (tx) => {
        this.transactions.set(tx);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger les données financières de la plateforme.');
      }
    });
  }

  simulate(t: WithdrawalView): void {
    if (!confirm(`Simuler le transfert de ${t.amount} $ vers ${t.teacherName} ? (aucun virement réel)`)) return;
    this.busyId.set(t.id);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.financeService.simulateTransfer(t.id).subscribe({
      next: (res) => {
        this.busyId.set(null);
        this.successMessage.set(`Transfert simulé ${res.transferReference} : ${res.amount} $ vers ${res.teacherName}.`);
        this.load();
      },
      error: (err) => {
        this.busyId.set(null);
        this.errorMessage.set(err.error?.message || 'Transfert impossible.');
      }
    });
  }

  txLabel(type: string): string {
    return this.txLabels[type] || type;
  }

  // Sens du mouvement pour le solde disponible du portefeuille concerné
  txSign(type: string): string {
    return ['SALE_EARNING', 'PLATFORM_COMMISSION', 'WITHDRAWAL_RELEASE'].includes(type) ? '+' : (type === 'WITHDRAWAL_HOLD' ? '−' : '');
  }
}
