import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AlertComponent,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  SpinnerComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { FinanceService, TeacherEarningsView } from '../../../services/finance.service';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
// « Mes revenus » : ventes de mes cours et ma part (60 %) de chaque vente confirmée
@Component({
  selector: 'app-teacher-earnings',
  standalone: true,
  imports: [
    CommonModule, RowComponent, ColComponent, CardComponent, CardHeaderComponent, CardBodyComponent,
    TableDirective, AlertComponent, SpinnerComponent, IconDirective,
    PageHeaderComponent,
    StatCardComponent
  ],
  templateUrl: './teacher-earnings.component.html'
})
export class TeacherEarningsComponent implements OnInit {
  data = signal<TeacherEarningsView | null>(null);
  loading = signal(false);
  errorMessage = signal('');

  constructor(private financeService: FinanceService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
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
}
