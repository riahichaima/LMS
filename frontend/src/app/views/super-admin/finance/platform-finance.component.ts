import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AlertComponent,
  BadgeComponent,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { FinanceService, PlatformWalletView, TransactionView } from '../../../services/finance.service';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
// « Contrôle financier » : portefeuille de la plateforme (40 % de chaque vente) et historique des mouvements
@Component({
  selector: 'app-platform-finance',
  standalone: true,
  imports: [
    CommonModule, RowComponent, ColComponent, CardComponent, CardHeaderComponent, CardBodyComponent, TableDirective,
    BadgeComponent, AlertComponent, IconDirective,
    PageHeaderComponent,
    StatCardComponent
  ],
  templateUrl: './platform-finance.component.html'
})
export class PlatformFinanceComponent implements OnInit {
  wallet = signal<PlatformWalletView | null>(null);
  transactions = signal<TransactionView[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  private readonly txLabels: Record<string, string> = {
    SALE_EARNING: 'Part enseignant (60 %)',
    PLATFORM_COMMISSION: 'Commission plateforme (40 %)'
  };

  constructor(private financeService: FinanceService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.financeService.getPlatformWallet().subscribe({ next: (w) => this.wallet.set(w) });
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

  txLabel(type: string): string {
    return this.txLabels[type] || type;
  }
}
