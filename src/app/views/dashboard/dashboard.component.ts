import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  AlertComponent,
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  BadgeComponent,
  ProgressComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService, User } from '../../services/auth.service';
import { AdminService, AdminStatistics, UserWithRoleDetails } from '../../services/admin.service';
import { LearningService, StudentStatistics } from '../../services/learning.service';
import { TrackingOverview, TrackingService, scoreColor } from '../../services/tracking.service';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { RoleLabelPipe, RoleTonePipe } from '../../shared/ui/labels';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    BadgeComponent,
    ProgressComponent,
    AlertComponent,
    SpinnerComponent,
    IconDirective,
    StatCardComponent,
    EmptyStateComponent,
    RoleLabelPipe,
    RoleTonePipe
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  currentUser: User | null = null;
  loading = signal(false);

  // Admin Data
  adminStats = signal<AdminStatistics | null>(null);
  recentUsers = signal<UserWithRoleDetails[]>([]);

  // Teacher Data (suivi pédagogique)
  tracking = signal<TrackingOverview | null>(null);
  trackingError = signal('');

  // Student Data (tableau de bord et statistiques fusionnés)
  stats = signal<StudentStatistics | null>(null);
  statsError = signal('');

  constructor(
    public authService: AuthService,
    private adminService: AdminService,
    private trackingService: TrackingService,
    private learningService: LearningService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserSignal();
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading.set(true);

    if (this.authService.isAdmin()) {
      this.adminService.getStatistics().subscribe({
        next: (stats) => this.adminStats.set(stats)
      });
      this.adminService.getUsers().subscribe({
        next: (users) => {
          this.recentUsers.set(users.slice(0, 5));
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
    } else if (this.authService.isTeacher()) {
      this.trackingService.getOverview().subscribe({
        next: (overview) => {
          this.tracking.set(overview);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.trackingError.set('Impossible de charger le suivi pédagogique. Vérifiez que le backend est démarré.');
        }
      });
    } else {
      this.learningService.getStatistics().subscribe({
        next: (s) => {
          this.stats.set(s);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.statsError.set('Impossible de charger vos statistiques. Vérifiez que le backend est démarré.');
        }
      });
    }
  }

  scoreColor(value: number | null): string {
    return scoreColor(value);
  }
}
