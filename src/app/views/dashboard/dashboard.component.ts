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
import { TeacherTrackingComponent } from '../teacher/tracking/teacher-tracking.component';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { inject } from '@angular/core';

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
    RoleTonePipe,
    TeacherTrackingComponent,
    ChartjsComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  /**
   * Étudiant : graphiques calculés sur ses vraies données (progression mesurée des cours,
   * quiz passés et TP rendus parmi ceux disponibles dans ses cours).
   */
  readonly studentCharts = computed(() => {
    const s = this.stats();
    if (!s) return null;
    const ring = (done: number, total: number) => {
      const pct = total > 0 ? Math.round(done * 100 / total) : 0;
      return { done, total, rest: Math.max(0, total - done), pct };
    };
    const coursePct = Math.round(s.averageProgress ?? 0);
    const quiz = ring(s.quizzesDone ?? 0, s.quizzesTotal ?? 0);
    const tp = ring(s.tpsDone ?? 0, s.tpsTotal ?? 0);
    const doughnut = (values: number[], colors: string[], labels: string[]) => ({
      labels,
      datasets: [{ data: values, backgroundColor: colors, borderWidth: 0, hoverOffset: 4 }]
    });
    return {
      courses: { pct: coursePct, data: doughnut([coursePct, 100 - coursePct], ['#1565e0', '#e3e9f3'], ['Lu (%)', 'Pas encore lu (%)']) },
      quiz: { ...quiz, data: doughnut(quiz.total ? [quiz.done, quiz.rest] : [0, 1], ['#1fa2ff', '#e3e9f3'], ['Quiz passés', 'Quiz restants']) },
      tp: { ...tp, data: doughnut(tp.total ? [tp.done, tp.rest] : [0, 1], ['#2eb85c', '#e3e9f3'], ['TP rendus', 'TP restants']) },
      bars: {
        labels: s.courses.map(c => c.title.length > 28 ? c.title.slice(0, 27) + '…' : c.title),
        datasets: [{
          label: 'Avancement (%)',
          data: s.courses.map(c => c.progressPercentage),
          backgroundColor: s.courses.map(c => (c.progressPercentage >= 100 ? '#2eb85c' : '#1565e0')),
          borderRadius: 6,
          maxBarThickness: 26
        }]
      }
    };
  });

  readonly doughnutOptions = {
    cutout: '72%',
    plugins: { legend: { display: false }, tooltip: { enabled: true } },
    maintainAspectRatio: false
  };

  readonly barOptions = {
    indexAxis: 'y' as const,
    maintainAspectRatio: false,
    plugins: { legend: { display: false }, tooltip: { callbacks: { label: (ctx: any) => ` ${ctx.parsed.x} %` } } },
    scales: {
      x: { min: 0, max: 100, ticks: { callback: (v: any) => v + ' %' }, grid: { color: 'rgba(120,130,150,.15)' } },
      y: { grid: { display: false }, ticks: { autoSkip: false } }
    }
  };

  /** Enseignant : vue d'ensemble ou suivi détaillé par cours (adresse : ?view=suivi&course=…) */
  teacherView = signal<'overview' | 'suivi'>('overview');

  setTeacherView(view: 'overview' | 'suivi', courseId?: number): void {
    this.teacherView.set(view);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: view === 'suivi' ? { view: 'suivi', course: courseId ?? this.route.snapshot.queryParamMap.get('course') } : {},
      replaceUrl: true
    });
  }
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
    // Liens « suivi » (anciennes adresses, notifications d'avis) : ouvrent directement l'onglet de suivi
    this.route.queryParamMap.subscribe(q => {
      if (q.get('view') === 'suivi' || q.get('course') || q.get('tab') === 'feedback') this.teacherView.set('suivi');
    });
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
