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
import { CourseChatbotComponent } from './course-chatbot.component';
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
    CourseChatbotComponent, CourseFeedbackComponent, FeedbackThreadComponent
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
    private route: ActivatedRoute,
    private learningService: LearningService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    this.learningService.getLearningView(this.courseId).subscribe({
      next: (view) => {
        this.view.set(view);
        this.progress.set(view.progressPercentage ?? 0);
        this.completed.set(new Set(view.completedLessonIds));
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
        this.progress.set(res.progressPercentage);
        this.completed.set(new Set(res.completedLessonIds));
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
