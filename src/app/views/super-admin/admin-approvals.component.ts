import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
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
import { AdminAccount, AdminService, AdminStatistics } from '../../services/admin.service';

// Espace du super administrateur : missions d'administration + demandes de compte administrateur
@Component({
  selector: 'app-admin-approvals',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RowComponent,
    ColComponent,
    CardComponent,
    CardHeaderComponent,
    CardBodyComponent,
    BadgeComponent,
    ButtonDirective,
    AlertComponent,
    TableDirective,
    IconDirective
  ],
  templateUrl: './admin-approvals.component.html'
})
export class AdminApprovalsComponent implements OnInit {
  admins = signal<AdminAccount[]>([]);
  stats = signal<AdminStatistics | null>(null);
  loading = signal(false);
  errorMessage = signal('');

  pending = computed(() => this.admins().filter(a => a.approvalStatus === 'PENDING'));
  approved = computed(() => this.admins().filter(a => a.approvalStatus === 'APPROVED'));

  // Missions d'administration, également accessibles au super administrateur
  readonly missions = [
    { icon: 'cilPeople', title: 'Utilisateurs', text: 'Activer, désactiver, changer le rôle ou supprimer un compte.', link: '/admin/users' },
    { icon: 'cilLibraryAdd', title: 'Formations', text: 'Superviser les cours et attribuer un formateur.', link: '/admin/courses' },
    { icon: 'cilSpeedometer', title: 'Tableau de bord', text: 'Statistiques et derniers inscrits de la plateforme.', link: '/dashboard' },
    { icon: 'cilLightbulb', title: 'Assistant IA', text: 'Interroger les contenus des cours avec l\'assistant.', link: '/ai-assistant' }
  ];

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.adminService.getStatistics().subscribe({ next: (s) => this.stats.set(s) });
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
}
