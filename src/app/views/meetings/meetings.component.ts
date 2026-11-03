import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../services/auth.service';
import { Course, CourseService } from '../../services/course.service';
import { MeetingService, MeetingView, RecordingView } from '../../services/meeting.service';

import { PageHeaderComponent } from '../../shared/ui/page-header.component';
interface CalendarDay {
  date: Date;
  key: string;
  inMonth: boolean;
  today: boolean;
  meetings: MeetingView[];
}

interface MeetingForm {
  id: number | null;
  courseId: number | null;
  title: string;
  description: string;
  startLocal: string; // valeur d'un <input type="datetime-local">
  durationMinutes: number;
}

/**
 * Réunions en visio : calendrier du mois, réunions à venir / passées, planification (formateur)
 * et onglet « Enregistrements » qui regroupe toutes les vidéos enregistrées pendant les réunions.
 */
@Component({
  selector: 'app-meetings',
  standalone: true,
  imports: [CommonModule, FormsModule, IconDirective,
    PageHeaderComponent
  ],
  templateUrl: './meetings.component.html',
  styleUrl: './meetings.component.scss'
})
export class MeetingsComponent implements OnInit, OnDestroy {
  private meetingService = inject(MeetingService);
  private courseService = inject(CourseService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly weekDays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  readonly durations = [15, 30, 45, 60, 90, 120, 180];

  readonly isTeacher = computed(() => this.auth.userRole().replace('ROLE_', '') === 'TEACHER');
  tab = signal<'meetings' | 'recordings'>('meetings');

  meetings = signal<MeetingView[]>([]);
  loading = signal(true);
  error = signal('');
  success = signal('');

  month = signal(this.startOfMonth(new Date()));
  selectedDay = signal<string | null>(null);

  // Planification
  courses = signal<Course[]>([]);
  formOpen = signal(false);
  form = signal<MeetingForm>(this.emptyForm());
  saving = signal(false);
  formError = signal('');

  // Enregistrements
  recordings = signal<RecordingView[]>([]);
  recordingsLoading = signal(false);
  recordingFilter = signal<number | null>(null);
  player = signal<{ recording: RecordingView; url: string } | null>(null);
  playerLoading = signal<number | null>(null);

  private refreshTimer: ReturnType<typeof setInterval> | null = null;

  readonly monthLabel = computed(() => this.month().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }));

  readonly calendar = computed<CalendarDay[]>(() => {
    const first = this.month();
    const offset = (first.getDay() + 6) % 7; // semaine commençant le lundi
    const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset);
    const byDay = new Map<string, MeetingView[]>();
    for (const m of this.meetings()) {
      const key = this.dayKey(new Date(m.startAt));
      byDay.set(key, [...(byDay.get(key) ?? []), m]);
    }
    const todayKey = this.dayKey(new Date());
    return Array.from({ length: 42 }, (_, i) => {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
      const key = this.dayKey(date);
      return { date, key, inMonth: date.getMonth() === first.getMonth(), today: key === todayKey, meetings: byDay.get(key) ?? [] };
    });
  });

  private readonly visible = computed(() => {
    const day = this.selectedDay();
    return day ? this.meetings().filter(m => this.dayKey(new Date(m.startAt)) === day) : this.meetings();
  });
  readonly upcoming = computed(() => this.visible().filter(m => m.status !== 'ENDED'));
  readonly past = computed(() => this.visible().filter(m => m.status === 'ENDED').reverse());
  readonly liveCount = computed(() => this.meetings().filter(m => m.status === 'LIVE').length);
  readonly selectedDayLabel = computed(() => {
    const day = this.selectedDay();
    if (!day) return '';
    const [y, mo, d] = day.split('-').map(Number);
    return new Date(y, mo - 1, d).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  });
  readonly meetingsWithRecordings = computed(() => {
    const seen = new Map<number, string>();
    for (const r of this.recordings()) if (!seen.has(r.meetingId)) seen.set(r.meetingId, r.meetingTitle);
    return [...seen.entries()].map(([id, title]) => ({ id, title }));
  });
  readonly filteredRecordings = computed(() => {
    const id = this.recordingFilter();
    return id ? this.recordings().filter(r => r.meetingId === id) : this.recordings();
  });

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const tab = params.get('tab') === 'recordings' ? 'recordings' : 'meetings';
      this.tab.set(tab);
      const meetingId = Number(params.get('meeting')) || null;
      this.recordingFilter.set(meetingId);
      if (tab === 'recordings') this.loadRecordings();
    });
    this.load();
    if (this.isTeacher()) {
      this.courseService.getTeacherCourses().subscribe({ next: c => this.courses.set(c), error: () => this.courses.set([]) });
    }
    // Statut (à venir / en cours / terminée) et bouton « Rejoindre » tenus à jour
    this.refreshTimer = setInterval(() => this.load(true), 30000);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    this.closePlayer();
  }

  load(silent = false): void {
    if (!silent) this.loading.set(true);
    this.meetingService.list().subscribe({
      next: list => {
        this.meetings.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        if (!silent) this.error.set(err.error?.message || 'Impossible de charger les réunions.');
      }
    });
  }

  setTab(tab: 'meetings' | 'recordings'): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: tab === 'recordings' ? 'recordings' : null, meeting: null } });
  }

  showRecordings(m: MeetingView): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: 'recordings', meeting: m.id } });
  }

  // ── Calendrier ──────────────────────────────────────────────

  shiftMonth(delta: number): void {
    const m = this.month();
    this.month.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
  }

  goToday(): void {
    this.month.set(this.startOfMonth(new Date()));
    this.selectedDay.set(this.dayKey(new Date()));
  }

  selectDay(day: CalendarDay): void {
    this.selectedDay.set(this.selectedDay() === day.key ? null : day.key);
    if (!day.inMonth) this.month.set(this.startOfMonth(day.date));
  }

  /** Double-clic sur un jour (formateur) : planifier une réunion ce jour-là. */
  planOn(day: CalendarDay): void {
    if (!this.isTeacher()) return;
    const date = new Date(day.date);
    const now = new Date();
    if (this.dayKey(date) === this.dayKey(now)) date.setHours(now.getHours() + 1, 0, 0, 0);
    else if (date < now) return;
    else date.setHours(10, 0, 0, 0);
    this.openCreate(date);
  }

  // ── Planification ───────────────────────────────────────────

  openCreate(at?: Date): void {
    const start = at ?? this.nextSlot();
    this.form.set({ ...this.emptyForm(), startLocal: this.toLocalInput(start), courseId: this.courses()[0]?.id ?? null });
    this.formError.set('');
    this.formOpen.set(true);
  }

  openEdit(m: MeetingView): void {
    this.form.set({
      id: m.id, courseId: m.courseId, title: m.title, description: m.description ?? '',
      startLocal: this.toLocalInput(new Date(m.startAt)), durationMinutes: m.durationMinutes
    });
    this.formError.set('');
    this.formOpen.set(true);
  }

  patchForm(patch: Partial<MeetingForm>): void {
    this.form.update(f => ({ ...f, ...patch }));
  }

  minStart(): string {
    return this.toLocalInput(new Date());
  }

  save(): void {
    const f = this.form();
    if (!f.courseId) return this.formError.set('Choisissez le cours concerné.');
    if (!f.title.trim()) return this.formError.set('Indiquez le sujet de la réunion.');
    if (!f.startLocal) return this.formError.set('Choisissez la date et l\'heure.');
    const start = new Date(f.startLocal);
    if (isNaN(start.getTime())) return this.formError.set('Date invalide.');
    if (start.getTime() < Date.now() - 60000) return this.formError.set('La date doit être dans le futur.');

    const request = {
      courseId: f.courseId, title: f.title.trim(), description: f.description.trim() || null,
      startAt: start.toISOString(), durationMinutes: f.durationMinutes
    };
    this.saving.set(true);
    this.formError.set('');
    const call = f.id ? this.meetingService.update(f.id, request) : this.meetingService.create(request);
    call.subscribe({
      next: m => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.success.set(f.id
          ? 'Réunion modifiée : les étudiants inscrits ont été prévenus.'
          : 'Réunion planifiée : les étudiants inscrits sont notifiés et recevront un rappel 1 h avant.');
        this.month.set(this.startOfMonth(new Date(m.startAt)));
        this.selectedDay.set(null);
        this.load(true);
      },
      error: err => {
        this.saving.set(false);
        this.formError.set(err.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  cancelMeeting(m: MeetingView): void {
    if (!confirm(`Annuler la réunion « ${m.title} » ? Les étudiants seront prévenus.`)) return;
    this.meetingService.cancel(m.id).subscribe({
      next: () => {
        this.success.set('Réunion annulée : les étudiants ont été prévenus.');
        this.load(true);
      },
      error: err => this.error.set(err.error?.message || 'Annulation impossible.')
    });
  }

  join(m: MeetingView): void {
    void this.router.navigate(['/meetings', m.id, 'room']);
  }

  // ── Enregistrements ─────────────────────────────────────────

  loadRecordings(): void {
    this.recordingsLoading.set(true);
    this.meetingService.recordings().subscribe({
      next: list => {
        this.recordings.set(list);
        this.recordingsLoading.set(false);
      },
      error: err => {
        this.recordingsLoading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger les enregistrements.');
      }
    });
  }

  filterRecordings(value: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: 'recordings', meeting: Number(value) || null } });
  }

  play(r: RecordingView): void {
    this.playerLoading.set(r.id);
    this.meetingService.recordingFile(r.id).subscribe({
      next: blob => {
        this.closePlayer();
        this.playerLoading.set(null);
        this.player.set({ recording: r, url: URL.createObjectURL(blob) });
      },
      error: () => {
        this.playerLoading.set(null);
        this.error.set('Lecture impossible : fichier introuvable.');
      }
    });
  }

  closePlayer(): void {
    const p = this.player();
    if (p) URL.revokeObjectURL(p.url);
    this.player.set(null);
  }

  download(r: RecordingView): void {
    this.meetingService.recordingFile(r.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const date = new Date(r.createdAt).toISOString().slice(0, 16).replace(/[:T]/g, '-');
        a.href = url;
        a.download = `${this.slug(r.meetingTitle)}-${date}.${r.mimeType.includes('mp4') ? 'mp4' : 'webm'}`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => this.error.set('Téléchargement impossible.')
    });
  }

  deleteRecording(r: RecordingView): void {
    if (!confirm('Supprimer définitivement cet enregistrement ?')) return;
    this.meetingService.deleteRecording(r.id).subscribe({
      next: () => {
        if (this.player()?.recording.id === r.id) this.closePlayer();
        this.recordings.update(list => list.filter(x => x.id !== r.id));
        this.success.set('Enregistrement supprimé.');
      },
      error: err => this.error.set(err.error?.message || 'Suppression impossible.')
    });
  }

  // ── Affichage ───────────────────────────────────────────────

  statusLabel(m: MeetingView): string {
    return m.status === 'LIVE' ? 'En cours' : m.status === 'ENDED' ? 'Terminée' : 'À venir';
  }

  /** « dans 2 h 15 », « demain à 10:00 »… */
  relative(m: MeetingView): string {
    const diff = new Date(m.startAt).getTime() - Date.now();
    if (m.status === 'LIVE') return `jusqu'à ${this.time(m.endAt)}`;
    if (m.status === 'ENDED' || diff < 0) return '';
    const minutes = Math.round(diff / 60000);
    if (minutes < 60) return `dans ${minutes} min`;
    if (minutes < 24 * 60) return `dans ${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
    return `dans ${Math.round(minutes / 1440)} j`;
  }

  monthShort(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '');
  }

  durationLabel(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    return `${Math.floor(minutes / 60)} h${minutes % 60 ? ` ${minutes % 60}` : ''}`;
  }

  time(iso: string): string {
    return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  size(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} Ko`;
    return `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  }

  duration(seconds: number | null): string {
    if (!seconds) return '—';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
  }

  // ── Outils ──────────────────────────────────────────────────

  private emptyForm(): MeetingForm {
    return { id: null, courseId: null, title: '', description: '', startLocal: '', durationMinutes: 60 };
  }

  private nextSlot(): Date {
    const d = new Date();
    d.setHours(d.getHours() + 1, 0, 0, 0);
    return d;
  }

  private startOfMonth(d: Date): Date {
    return new Date(d.getFullYear(), d.getMonth(), 1);
  }

  private dayKey(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private toLocalInput(d: Date): string {
    return `${this.dayKey(d)}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  private slug(text: string): string {
    return (text || 'reunion').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase() || 'reunion';
  }
}
