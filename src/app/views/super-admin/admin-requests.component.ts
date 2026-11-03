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
  SpinnerComponent,
  TableDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AdminAccount, AdminService } from '../../services/admin.service';

import { PageHeaderComponent } from '../../shared/ui/page-header.component';
// Demandes de compte administrateur : le super administrateur approuve ou non
@Component({
  selector: 'app-admin-requests',
  standalone: true,
  imports: [
    CommonModule,
    RowComponent,
    ColComponent,
    CardComponent,
    CardHeaderComponent,
    CardBodyComponent,
    TableDirective,
    BadgeComponent,
    ButtonDirective,
    AlertComponent,
    SpinnerComponent,
    IconDirective,
    PageHeaderComponent
  ],
  templateUrl: './admin-requests.component.html'
})
export class AdminRequestsComponent implements OnInit {
  admins = signal<AdminAccount[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  // Demande en cours de traitement (boutons désactivés pendant la requête)
  busyId = signal<number | null>(null);

  pending = computed(() => this.admins().filter(a => a.approvalStatus === 'PENDING'));

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.adminService.getAdminAccounts().subscribe({
      next: (admins) => {
        this.loading.set(false);
        this.admins.set(admins);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger les demandes de compte administrateur.');
      }
    });
  }

  // Approuver : la personne devient administrateur
  approve(admin: AdminAccount): void {
    this.busyId.set(admin.id);
    this.clearMessages();
    this.adminService.approveAdmin(admin.id).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.admins.update(list => list.map(a => (a.id === updated.id ? updated : a)));
        this.successMessage.set(`${admin.name} est maintenant administrateur.`);
      },
      error: (err) => this.fail(err)
    });
  }

  // Ne pas approuver : la demande est refusée et le compte supprimé
  reject(admin: AdminAccount): void {
    if (!confirm(`Ne pas approuver ${admin.name} ? Son compte sera supprimé.`)) return;
    this.busyId.set(admin.id);
    this.clearMessages();
    this.adminService.rejectAdmin(admin.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.admins.update(list => list.filter(a => a.id !== admin.id));
        this.successMessage.set(`La demande de ${admin.name} n'a pas été approuvée : son compte a été supprimé.`);
      },
      error: (err) => this.fail(err)
    });
  }

  private clearMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  private fail(err: any): void {
    this.busyId.set(null);
    this.errorMessage.set(err.error?.message || 'La décision n\'a pas pu être enregistrée.');
  }
}
