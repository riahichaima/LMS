import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  ProgressComponent,
  RowComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { Chapter, Lesson, LessonResource } from '../../services/course.service';
import { AssessmentSummary, LearningService, LearningView } from '../../services/learning.service';
import { HttpEventType } from '@angular/common/http';
import { PdfViewerComponent } from '../../shared/pdf-viewer.component';
import { ACCEPTED_FILES, AssessmentFile, AssessmentFileService, fileSize } from '../../services/assessment-file.service';
import { CourseChatbotComponent } from './course-chatbot.component';
import { AssessmentScheduleService, ScheduleSummary, formatDue, formatDuration } from '../../services/assessment-schedule.service';
import { CountdownComponent } from '../../shared/countdown.component';
import { CourseFeedbackComponent } from '../../shared/course-feedback.component';
import { FeedbackThreadComponent } from '../../shared/feedback-thread.component';

type FileState = { status: 'loading' } | { status: 'ready'; url: string; safeUrl: SafeResourceUrl } | { status: 'error' };

const LEVEL_LABELS: Record<string, string> = {
  BEGINNER: 'Débutant',
  INTERMEDIATE: 'Intermédiaire',
  ADVANCED: 'Avancé'
};

/**
 * Lecture d'un cours par l'étudiant inscrit (gratuit ou acheté) : PDF affichés dans la page,
 * vidéos lues dans le lecteur, liens externes ouverts dans un nouvel onglet, progression par leçon,
 * quiz/TP du chapitre et tuteur IA.
 */
@Component({
  selector: 'app-course-player',
  standalone: true,
  imports: [
    CommonModule, RouterLink, AlertComponent, BadgeComponent, ButtonDirective, CardComponent, CardBodyComponent,
    CardHeaderComponent, ColComponent, ProgressComponent, RowComponent, SpinnerComponent, IconDirective,
    CourseChatbotComponent, CourseFeedbackComponent, FeedbackThreadComponent, PdfViewerComponent, CountdownComponent
  ],
  templateUrl: './course-player.component.html',
  styleUrl: './course-player.component.scss'
})
export class CoursePlayerComponent implements OnInit, OnDestroy {
  view = signal<LearningView | null>(null);
  loading = signal(true);
  error = signal('');
  completing = signal(false);
  progress = signal(0);
  completed = signal<Set<number>>(new Set());
  currentLesson = signal<Lesson | null>(null);
  // Fichiers (PDF, vidéo, audio) chargés avec le jeton puis affichés via une URL blob
  files = signal<Record<number, FileState>>({});
  /** Avancement réel (%) de chaque leçon et de chaque ressource */
  lessonPct = signal<Record<number, number>>({});
  resourcePct = signal<Record<number, number>>({});
  /** Quiz / TP importés par l'enseignant : dépôt de copie en cours */
  uploadingCopy = signal<number | null>(null);
  copyProgress = signal(0);
  copyMessage = signal('');
  readonly fileSize = fileSize;
  readonly formatDue = formatDue;
  readonly formatDuration = formatDuration;
  /** Sujet minuté en cours de démarrage */
  startingFile = signal<number | null>(null);
  readonly accepted = ACCEPTED_FILES;
  private lastSent: Record<number, number> = {};

  readonly levelLabels = LEVEL_LABELS;

  chapters = computed<Chapter[]>(() =>
    [...(this.view()?.course.chapters ?? [])]
      .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
      .map(ch => ({ ...ch, lessons: [...(ch.lessons ?? [])].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)) }))
  );
  allLessons = computed<Lesson[]>(() => this.chapters().flatMap(ch => ch.lessons));
  nextLesson = computed<Lesson | null>(() => {
    const lessons = this.allLessons();
    const index = lessons.findIndex(l => l.id === this.currentLesson()?.id);
    return index >= 0 && index < lessons.length - 1 ? lessons[index + 1] : null;
  });

  private courseId = 0;

  constructor(
    private scheduleService: AssessmentScheduleService,
    private route: ActivatedRoute,
    private learningService: LearningService,
    private sanitizer: DomSanitizer,
    private fileService: AssessmentFileService
  ) {}

  ngOnInit(): void {
    // Passage direct d'un cours à un autre (même page réutilisée) : on recharge tout
    this.route.paramMap.subscribe(params => {
      const id = Number(params.get('courseId'));
      if (id && id !== this.courseId) this.loadCourse(id);
    });
  }

  private loadCourse(courseId: number): void {
    this.courseId = courseId;
    this.revokeFiles();
    this.view.set(null);
    this.currentLesson.set(null);
    this.lessonPct.set({});
    this.resourcePct.set({});
    this.lastSent = {};
    this.error.set('');
    this.loading.set(true);
    this.learningService.getLearningView(this.courseId).subscribe({
      next: (view) => {
        this.view.set(view);
        this.applyProgress(view);
        this.loading.set(false);
        const lessons = this.allLessons();
        const first = lessons.find(l => !this.completed().has(l.id!)) ?? lessons[0];
        if (first) this.selectLesson(first);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 403
          ? (err.error?.message || 'Inscrivez-vous à ce cours (ou achetez-le) pour y accéder.')
          : 'Impossible de charger le cours. Vérifiez que le backend est démarré.');
      }
    });
  }

  ngOnDestroy(): void {
    this.revokeFiles();
  }

  selectLesson(lesson: Lesson): void {
    this.revokeFiles();
    this.currentLesson.set(lesson);
    for (const r of lesson.resources ?? []) {
      if (this.isFile(r)) this.loadFile(r);
      // Un simple texte est lu dès que la leçon est ouverte
      if (r.id && this.kind(r) === 'text') this.report(r, 100, true);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  markCompleted(): void {
    const lesson = this.currentLesson();
    if (!lesson?.id || this.completing()) return;
    this.completing.set(true);
    this.learningService.completeLesson(lesson.id).subscribe({
      next: (res) => {
        this.completing.set(false);
        this.applyProgress(res);
        const next = this.nextLesson();
        if (next) this.selectLesson(next);
      },
      error: () => {
        this.completing.set(false);
        this.error.set("La progression n'a pas pu être enregistrée.");
      }
    });
  }

  assessmentsOf(chapter: Chapter): { quizzes: AssessmentSummary[]; assignments: AssessmentSummary[] } {
    const found = this.view()?.assessments.find(a => a.chapterId === chapter.id);
    return { quizzes: found?.quizzes ?? [], assignments: found?.assignments ?? [] };
  }

  isDone(lesson: Lesson): boolean {
    return this.completed().has(lesson.id!);
  }

  // ── Avancement réel ─────────────────────────────────────────

  lessonPercent(lesson: Lesson): number {
    return this.isDone(lesson) ? 100 : this.lessonPct()[lesson.id!] ?? 0;
  }

  pct(r: LessonResource): number {
    return this.resourcePct()[r.id!] ?? 0;
  }

  onPdfProgress(r: LessonResource, percent: number): void {
    this.report(r, percent);
  }

  /** Vidéo / audio : part la plus avancée atteinte (en %) */
  onMediaTime(r: LessonResource, event: Event): void {
    const media = event.target as HTMLMediaElement;
    if (!media.duration || !isFinite(media.duration)) return;
    this.report(r, (media.currentTime / media.duration) * 100);
  }

  onMediaEnded(r: LessonResource): void {
    this.report(r, 100, true);
  }

  /** Lien externe ouvert, vidéo YouTube regardée (non mesurable automatiquement) */
  markResourceDone(r: LessonResource): void {
    this.report(r, 100, true);
  }

  /** Envoi groupé : tous les 5 points de progression, ou tout de suite à la fin. */
  private report(r: LessonResource, percent: number, force = false): void {
    const id = r.id!;
    const value = Math.max(0, Math.min(100, Math.round(percent)));
    const known = Math.max(this.pct(r), this.lastSent[id] ?? 0);
    if (value <= known) return;
    if (!force && value < 100 && value - known < 5) return;
    this.lastSent[id] = value;
    this.resourcePct.update(m => ({ ...m, [id]: value }));
    this.learningService.updateResourceProgress(id, value).subscribe({
      next: res => this.applyProgress(res),
      error: () => { /* nouvel essai au prochain palier */ delete this.lastSent[id]; }
    });
  }

  private applyProgress(res: { progressPercentage: number; completedLessonIds: number[]; lessonProgress?: Record<number, number>; resourceProgress?: Record<number, number> }): void {
    this.progress.set(res.progressPercentage ?? 0);
    this.completed.set(new Set(res.completedLessonIds ?? []));
    if (res.lessonProgress) this.lessonPct.set(res.lessonProgress);
    if (res.resourceProgress) {
      // Ne jamais faire reculer l'affichage d'une progression locale plus récente
      const merged = { ...res.resourceProgress };
      for (const [k, v] of Object.entries(this.resourcePct())) merged[+k] = Math.max(v, merged[+k] ?? 0);
      this.resourcePct.set(merged);
    }
  }

  // ── Quiz / TP importés par l'enseignant ─────────────────────

  filesOf(chapter: Chapter): AssessmentFile[] {
    return (this.view()?.files ?? []).filter(f => f.chapterId === chapter.id);
  }

  allFiles(): AssessmentFile[] {
    return this.view()?.files ?? [];
  }

  /**
   * État d'un quiz / TP pour l'étudiant :
   * open (libre), gate (minuté, pas encore commencé), running (chrono en cours),
   * done (rendu / temps écoulé), closed (date limite dépassée sans passage).
   */
  scheduleState(s: ScheduleSummary | null | undefined, isFile = false): 'open' | 'gate' | 'running' | 'done' | 'closed' {
    if (!s) return 'open';
    // Sujet importé : la copie reste remplaçable jusqu'à la fin du chrono
    if (s.startedAt) return s.expired || (s.submitted && !isFile) ? 'done' : 'running';
    if (s.closed) return 'closed';
    return s.durationMinutes ? 'gate' : 'open';
  }

  /** Sujet importé minuté : démarre le chrono puis télécharge le sujet */
  startFile(f: AssessmentFile): void {
    this.startingFile.set(f.id);
    this.copyMessage.set('');
    this.scheduleService.start('FILE', f.id).subscribe({
      next: st => {
        this.startingFile.set(null);
        const schedule: ScheduleSummary = {
          dueAt: st.dueAt, durationMinutes: st.durationMinutes, closed: st.closed,
          startedAt: st.startedAt, endsAt: st.endsAt, submitted: st.submitted, expired: st.expired
        };
        this.view.update(v => v ? { ...v, serverNow: st.serverNow, files: v.files.map(x => (x.id === f.id ? { ...x, schedule } : x)) } : v);
        this.downloadSubject(f);
      },
      error: err => {
        this.startingFile.set(null);
        this.copyMessage.set(err.error?.message || 'Impossible de commencer ce sujet.');
      }
    });
  }

  onFileTimeUp(f: AssessmentFile): void {
    this.view.update(v => v ? { ...v, files: v.files.map(x => (x.id === f.id && x.schedule ? { ...x, schedule: { ...x.schedule, expired: true } } : x)) } : v);
    this.copyMessage.set(`Temps écoulé pour « ${f.title} » : le dépôt de copie est fermé.`);
  }

  /** Dépôt de copie possible (pas corrigée, pas fermé, chrono non écoulé) */
  canUpload(f: AssessmentFile): boolean {
    if (f.mySubmission?.grade != null) return false;
    const state = this.scheduleState(f.schedule, true);
    return (state === 'open' || state === 'running') && !f.schedule?.closed;
  }

  downloadSubject(f: AssessmentFile): void {
    this.fileService.downloadSubject(f.id).subscribe({
      next: blob => AssessmentFileService.save(blob, f.originalName),
      error: () => this.error.set('Le sujet n\'a pas pu être téléchargé.')
    });
  }

  downloadCopy(f: AssessmentFile): void {
    const s = f.mySubmission;
    if (!s) return;
    this.fileService.downloadSubmission(s.id).subscribe({
      next: blob => AssessmentFileService.save(blob, s.originalName),
      error: () => this.error.set('La copie n\'a pas pu être téléchargée.')
    });
  }

  submitCopy(f: AssessmentFile, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 25 * 1024 * 1024) { this.copyMessage.set('Fichier trop volumineux (25 Mo maximum).'); return; }
    this.uploadingCopy.set(f.id);
    this.copyProgress.set(0);
    this.copyMessage.set('');
    this.fileService.submit(f.id, file).subscribe({
      next: ev => {
        if (ev.type === HttpEventType.UploadProgress && ev.total) this.copyProgress.set(Math.round(ev.loaded * 100 / ev.total));
        if (ev.type === HttpEventType.Response && ev.body) {
          const sub = ev.body;
          this.uploadingCopy.set(null);
          this.view.update(v => v ? { ...v, files: v.files.map(x => (x.id === f.id ? { ...x, mySubmission: sub } : x)) } : v);
          this.copyMessage.set(`Copie « ${sub.originalName} » rendue : votre enseignant est notifié.`);
        }
      },
      error: err => {
        this.uploadingCopy.set(null);
        this.copyMessage.set(err.error?.message || 'La copie n\'a pas pu être envoyée.');
      }
    });
  }

  // ── Ressources ─────────────────────────────────────────────

  kind(r: LessonResource): 'pdf' | 'video' | 'audio' | 'youtube' | 'link' | 'text' | 'file' {
    const url = r.externalUrl || '';
    if (r.resourceType === 'YOUTUBE_VIDEO' || /youtu\.?be/.test(url)) return 'youtube';
    if (r.resourceType === 'PDF') return 'pdf';
    if (r.resourceType === 'VIDEO') return 'video';
    if (r.resourceType === 'AUDIO') return 'audio';
    if (r.resourceType === 'TEXT' && !url && !r.filePath) return 'text';
    if (url) return 'link';
    return 'file';
  }

  file(r: LessonResource): FileState | undefined {
    return this.files()[r.id!];
  }

  youtubeEmbed(r: LessonResource): SafeResourceUrl | null {
    const url = r.externalUrl || '';
    const match = url.match(/(?:youtu\.be\/|v=|embed\/|shorts\/)([\w-]{11})/);
    return match ? this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${match[1]}`) : null;
  }

  download(r: LessonResource): void {
    const state = this.file(r);
    if (state?.status !== 'ready') return;
    const a = document.createElement('a');
    a.href = state.url;
    a.download = r.fileName || r.title || 'ressource';
    a.click();
  }

  private isFile(r: LessonResource): boolean {
    return !!r.id && ['pdf', 'video', 'audio', 'file'].includes(this.kind(r)) && !!(r.filePath || r.fileName || r.mimeType);
  }

  private loadFile(r: LessonResource): void {
    const id = r.id!;
    this.files.update(f => ({ ...f, [id]: { status: 'loading' } }));
    this.learningService.getResourceFile(id).subscribe({
      next: (blob) => {
        // Type MIME fiable pour l'affichage intégré (le serveur peut renvoyer octet-stream)
        const type = this.kind(r) === 'pdf' ? 'application/pdf' : (r.mimeType || blob.type);
        const url = URL.createObjectURL(type ? new Blob([blob], { type }) : blob);
        this.files.update(f => ({ ...f, [id]: { status: 'ready', url, safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(url) } }));
      },
      error: () => this.files.update(f => ({ ...f, [id]: { status: 'error' } }))
    });
  }

  private revokeFiles(): void {
    Object.values(this.files()).forEach(f => { if (f.status === 'ready') URL.revokeObjectURL(f.url); });
    this.files.set({});
  }
}
