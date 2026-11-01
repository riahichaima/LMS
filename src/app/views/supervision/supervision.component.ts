import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  AlertComponent,
  BadgeComponent,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  ColorModeService,
  ProgressComponent,
  RowComponent,
  SpinnerComponent
} from '@coreui/angular';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { IconDirective } from '@coreui/icons-angular';
import { ChartData, ChartOptions } from 'chart.js';
import { GlobalStats, LogFilters, LogPage, SupervisionService, SystemLog } from '../../services/supervision.service';
import { saveBlob, scoreColor } from '../../services/tracking.service';

/** Palette catégorielle validée (3 premiers créneaux : distincts aussi pour les daltoniens), clair / sombre. */
const SERIES = {
  light: ['#2a78d6', '#eb6834', '#1baf7a'],
  dark: ['#3987e5', '#d95926', '#199e70']
};

/**
 * Supervision (administrateur et super administrateur) : statistiques globales de la plateforme
 * et journal système (connexions, actions sensibles, accès refusés, erreurs serveur).
 */
@Component({
  selector: 'app-supervision',
  standalone: true,
  imports: [
    CommonModule, FormsModule, AlertComponent, BadgeComponent, CardComponent, CardBodyComponent,
    CardHeaderComponent, ColComponent, ProgressComponent, RowComponent, SpinnerComponent, ChartjsComponent, IconDirective
  ],
  templateUrl: './supervision.component.html',
  styleUrl: './supervision.component.scss'
})
export class SupervisionComponent implements OnInit {
  readonly tab = signal<'stats' | 'logs'>('stats');
  readonly scoreColor = scoreColor;

  // ── Statistiques ──
  stats = signal<GlobalStats | null>(null);
  statsLoading = signal(true);
  statsError = signal('');
  showTable = signal(false);

  private colorMode = inject(ColorModeService).colorMode;
  private dark = signal(false);

  readonly roles = computed(() => {
    const s = this.stats();
    if (!s) return [];
    const labels: Record<string, string> = { STUDENT: 'Étudiants', TEACHER: 'Formateurs', ADMIN: 'Administrateurs', SUPER_ADMIN: 'Super admin' };
    const max = Math.max(1, ...Object.values(s.users.byRole));
    return Object.entries(s.users.byRole).map(([role, count]) => ({ label: labels[role] ?? role, count, pct: Math.round(count * 100 / max) }));
  });

  readonly activitySeries = computed(() => {
    const s = this.stats();
    if (!s) return [];
    return [
      { label: 'Connexions', data: s.series.logins },
      { label: 'Inscriptions aux cours', data: s.series.enrollments },
      { label: 'Quiz et TP rendus', data: s.series.submissions }
    ];
  });

  readonly chartData = computed<ChartData<'line'>>(() => {
    const s = this.stats();
    const colors = this.dark() ? SERIES.dark : SERIES.light;
    return {
      labels: s ? s.series.days.map(d => this.shortDay(d)) : [],
      datasets: this.activitySeries().map((serie, i) => ({
        label: serie.label,
        data: serie.data,
        borderColor: colors[i],
        backgroundColor: colors[i],
        borderWidth: 2,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHitRadius: 12,
        tension: 0.25
      }))
    };
  });

  readonly chartOptions = computed<ChartOptions<'line'>>(() => {
    const css = getComputedStyle(document.documentElement);
    const text = css.getPropertyValue('--cui-secondary-color').trim() || '#6b7280';
    const grid = css.getPropertyValue('--cui-border-color-translucent').trim() || 'rgba(0,0,0,0.08)';
    this.dark(); // recalcul au changement de thème
    return {
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'top', align: 'start', labels: { color: text, usePointStyle: true, pointStyle: 'line', boxWidth: 24 } },
        tooltip: { mode: 'index', intersect: false }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: text, maxRotation: 0, autoSkip: true, maxTicksLimit: 10 } },
        y: { beginAtZero: true, grid: { color: grid }, border: { display: false }, ticks: { color: text, precision: 0 } }
      }
    };
  });

  // ── Journal système ──
  logs = signal<LogPage | null>(null);
  logsLoading = signal(false);
  logsError = signal('');
  exporting = signal(false);
  page = signal(0);
  readonly pageSize = 25;
  filters: LogFilters = { level: '', category: '', q: '', from: '', to: '' };
  expanded = signal<number | null>(null);

  readonly levels = ['INFO', 'WARN', 'ERROR', 'SECURITY'];
  readonly categories: Record<string, string> = {
    AUTH: 'Authentification', USER: 'Utilisateurs', COURSE: 'Cours', ENROLLMENT: 'Inscriptions', LEARNING: 'Apprentissage',
    PAYMENT: 'Paiements', FINANCE: 'Finance', MESSAGE: 'Messagerie', AI: 'IA', SYSTEM: 'Système', API: 'API'
  };
  readonly categoryKeys = Object.keys(this.categories);

  constructor(private supervision: SupervisionService) {
    effect(() => {
      const mode = this.colorMode();
      const prefersDark = typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches;
      this.dark.set(mode === 'dark' || (mode === 'auto' && !!prefersDark));
    });
  }

  ngOnInit(): void {
    this.loadStats();
  }

  setTab(tab: 'stats' | 'logs'): void {
    this.tab.set(tab);
    if (tab === 'logs' && !this.logs()) this.loadLogs();
  }

  loadStats(): void {
    this.statsLoading.set(true);
    this.statsError.set('');
    this.supervision.getStats().subscribe({
      next: (s) => {
        this.stats.set(s);
        this.statsLoading.set(false);
      },
      error: () => {
        this.statsLoading.set(false);
        this.statsError.set('Impossible de charger les statistiques globales.');
      }
    });
  }

  applyFilters(): void {
    this.page.set(0);
    this.loadLogs();
  }

  resetFilters(): void {
    this.filters = { level: '', category: '', q: '', from: '', to: '' };
    this.applyFilters();
  }

  goTo(page: number): void {
    const total = this.logs()?.totalPages ?? 1;
    if (page < 0 || page >= total) return;
    this.page.set(page);
    this.loadLogs();
  }

  loadLogs(): void {
    this.logsLoading.set(true);
    this.logsError.set('');
    this.supervision.getLogs(this.filters, this.page(), this.pageSize).subscribe({
      next: (p) => {
        this.logs.set(p);
        this.logsLoading.set(false);
      },
      error: () => {
        this.logsLoading.set(false);
        this.logsError.set('Impossible de charger le journal système.');
      }
    });
  }

  exportLogs(): void {
    this.exporting.set(true);
    this.supervision.exportLogs(this.filters).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        saveBlob(blob, `logs_systeme_${new Date().toISOString().slice(0, 10)}.csv`);
      },
      error: () => {
        this.exporting.set(false);
        this.logsError.set("L'export du journal a échoué.");
      }
    });
  }

  /** Raccourci : voir les logs d'un niveau sur les dernières 24 h. */
  showLevel(level: string): void {
    this.filters = { level, category: '', q: '', from: '', to: '' };
    this.page.set(0);
    this.tab.set('logs');
    this.loadLogs();
  }

  toggle(log: SystemLog): void {
    this.expanded.update(id => (id === log.id ? null : log.id));
  }

  levelColor(level: string): string {
    return level === 'ERROR' ? 'danger' : level === 'WARN' ? 'warning' : level === 'SECURITY' ? 'dark' : 'info';
  }

  levelLabel(level: string): string {
    return level === 'ERROR' ? 'Erreur' : level === 'WARN' ? 'Avertissement' : level === 'SECURITY' ? 'Sécurité' : 'Info';
  }

  shortDay(iso: string): string {
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }
}
