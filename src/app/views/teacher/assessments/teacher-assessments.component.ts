import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AlertComponent, ButtonDirective, SpinnerComponent } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { CourseService, Course, Chapter } from '../../../services/course.service';
import { AiService, AiModelStatus } from '../../../services/ai.service';
import { AuthService } from '../../../services/auth.service';
import { formatDue, formatDuration, fromLocalInput } from '../../../services/assessment-schedule.service';

interface GeneratedItem {
  kind: 'Quiz' | 'TP';
  title: string;
  level: string;
  count: number;
}

/**
 * Génération de quiz et de TP par niveau avec l'IA, à partir du contenu du cours choisi
 * (un ou plusieurs chapitres, ou tout le cours ; PDF, vidéo, audio, YouTube, liens, texte),
 * avec la date limite de rendu et le temps de travail.
 */
@Component({
  selector: 'app-teacher-assessments',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertComponent, ButtonDirective, SpinnerComponent, IconDirective],
  templateUrl: './teacher-assessments.component.html',
  styleUrl: './teacher-assessments.component.scss'
})
export class TeacherAssessmentsComponent implements OnInit {
  private destroyRef = inject(DestroyRef);

  courses = signal<Course[]>([]);
  selectedCourseId = signal<number | undefined>(undefined);
  chapters = signal<Chapter[]>([]);
  selectedChapterIds = signal<number[]>([]);

  generateQuiz = true;
  generateAssignment = true;
  quizQuestionCount = 5;
  assignmentExerciseCount = 3;

  readonly levels = [
    { key: 'BEGINNER', label: 'Débutant', hint: 'Notions de base, questions directes' },
    { key: 'INTERMEDIATE', label: 'Intermédiaire', hint: 'Application et compréhension' },
    { key: 'ADVANCED', label: 'Avancé', hint: 'Analyse, cas complexes' }
  ];
  selectedLevels = signal<string[]>(['BEGINNER', 'INTERMEDIATE', 'ADVANCED']);

  // Rendu : date limite (calendrier) et temps de travail
  dueAtInput = '';
  quizTimed = false;
  quizDuration = 20;
  assignmentTimed = false;
  assignmentDuration = 120;
  readonly quizPresets = [10, 15, 20, 30, 45, 60];
  readonly assignmentPresets = [60, 90, 120, 180, 240];

  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  generated = signal<GeneratedItem[]>([]);
  // Secondes écoulées depuis le lancement de la génération
  elapsed = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  modelStatus = signal<AiModelStatus | null>(null);

  readonly formatDuration = formatDuration;
  readonly formatDue = formatDue;

  // Formats de contenu reconnus par l'IA (URL = ancien type de lien web)
  private readonly sourceLabels: Record<string, string> = {
    PDF: 'PDF', VIDEO: 'Vidéo', AUDIO: 'Audio', YOUTUBE_VIDEO: 'YouTube', WEB_PAGE: 'Lien web', URL: 'Lien web', TEXT: 'Texte'
  };
  private readonly sourceIcons: Record<string, string> = {
    PDF: 'cilFile', VIDEO: 'cilMediaPlay', AUDIO: 'cilMicrophone', YOUTUBE_VIDEO: 'cilVideo', WEB_PAGE: 'cilLink', URL: 'cilLink', TEXT: 'cilNotes'
  };

  allSelected = computed(() => this.chapters().length > 0 && this.selectedChapterIds().length === this.chapters().length);

  /** Formats présents dans les chapitres choisis, avec leur nombre */
  availableSources = computed(() => {
    const ids = new Set(this.selectedChapterIds());
    const counts = new Map<string, number>();
    for (const chapter of this.chapters().filter(c => ids.has(c.id!))) {
      for (const lesson of chapter.lessons || []) {
        for (const r of lesson.resources || []) {
          counts.set(r.resourceType, (counts.get(r.resourceType) || 0) + 1);
        }
      }
    }
    return Object.keys(this.sourceLabels)
      .filter(type => counts.has(type))
      .map(type => ({ type, label: this.sourceLabels[type], icon: this.sourceIcons[type], count: counts.get(type)! }));
  });
  // Types décochés par l'enseignant (tout est coché par défaut)
  excludedSources = signal<string[]>([]);

  constructor(
    private courseService: CourseService,
    private aiService: AiService,
    private authService: AuthService
  ) {
    this.destroyRef.onDestroy(() => this.stopTimer());
  }

  ngOnInit(): void {
    this.aiService.getModelStatus().subscribe({
      next: (status) => this.modelStatus.set(status),
      error: () => this.modelStatus.set({ status: 'down', model_available: false, error: 'Backend injoignable.' })
    });

    this.courseService.getTeacherCourses().subscribe({
      next: (courses) => {
        this.courses.set(courses);
        if (courses.length > 0) {
          this.selectedCourseId.set(courses[0].id);
          this.onCourseChange();
        }
      }
    });
  }

  onCourseChange(): void {
    const courseId = this.selectedCourseId();
    this.chapters.set([]);
    this.selectedChapterIds.set([]);
    this.excludedSources.set([]);
    if (!courseId) return;
    this.courseService.getCourseChapters(courseId).subscribe({
      next: (chaps) => {
        this.chapters.set(chaps);
        // Par défaut : tout le cours
        this.selectedChapterIds.set(chaps.map(c => c.id!));
      }
    });
  }

  toggleAll(checked: boolean): void {
    this.selectedChapterIds.set(checked ? this.chapters().map(c => c.id!) : []);
  }

  isChapterSelected(id: number | undefined): boolean {
    return id != null && this.selectedChapterIds().includes(id);
  }

  toggleChapter(id: number | undefined, checked: boolean): void {
    if (id == null) return;
    this.selectedChapterIds.update(list => checked ? [...new Set([...list, id])] : list.filter(x => x !== id));
  }

  /** Formats d'un chapitre (affichés sous son titre) */
  chapterFormats(chapter: Chapter): { label: string; icon: string; count: number }[] {
    const counts = new Map<string, number>();
    for (const lesson of chapter.lessons || []) {
      for (const r of lesson.resources || []) counts.set(r.resourceType, (counts.get(r.resourceType) || 0) + 1);
    }
    return [...counts.entries()].map(([type, count]) => ({
      label: this.sourceLabels[type] || type, icon: this.sourceIcons[type] || 'cilFile', count
    }));
  }

  isLevelSelected(key: string): boolean {
    return this.selectedLevels().includes(key);
  }

  toggleLevel(key: string): void {
    this.selectedLevels.update(list => list.includes(key) ? list.filter(k => k !== key) : [...list, key]);
  }

  isSourceSelected(type: string): boolean {
    return !this.excludedSources().includes(type);
  }

  toggleSource(type: string, checked: boolean): void {
    this.excludedSources.update(list => checked ? list.filter(t => t !== type) : [...list, type]);
  }

  selectedSourceTypes(): string[] {
    return this.availableSources().map(s => s.type).filter(t => this.isSourceSelected(t));
  }

  /** Date minimale du calendrier : maintenant */
  get minDue(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  get dueIso(): string | null {
    return fromLocalInput(this.dueAtInput);
  }

  get scopeLabel(): string {
    const n = this.selectedChapterIds().length;
    if (n === 0) return 'Aucun chapitre';
    if (this.allSelected()) return 'Tout le cours';
    return n === 1 ? (this.chapters().find(c => c.id === this.selectedChapterIds()[0])?.title || '1 chapitre') : `${n} chapitres`;
  }

  levelLabel(key: string): string {
    return this.levels.find(l => l.key === key)?.label || key;
  }

  // Nombre d'appels au modèle pour la demande en cours (affiché comme estimation)
  get callCount(): number {
    return this.selectedLevels().length * ((this.generateQuiz ? 1 : 0) + (this.generateAssignment ? 1 : 0));
  }

  onGenerate(): void {
    const chapterIds = this.selectedChapterIds();
    const levels = this.levels.map(l => l.key).filter(k => this.isLevelSelected(k));
    if (chapterIds.length === 0) return this.errorMessage.set('Choisissez au moins un chapitre (ou tout le cours).');
    if (levels.length === 0) return this.errorMessage.set('Choisissez au moins un niveau de difficulté.');
    if (!this.generateQuiz && !this.generateAssignment) return this.errorMessage.set('Cochez au moins « Quiz » ou « TP ».');
    const resourceTypes = this.selectedSourceTypes();
    if (this.availableSources().length > 0 && resourceTypes.length === 0) {
      return this.errorMessage.set('Cochez au moins un format de contenu (PDF, vidéo, lien...).');
    }
    if (this.dueAtInput && (!this.dueIso || new Date(this.dueIso).getTime() <= Date.now())) {
      return this.errorMessage.set('La date limite doit être dans le futur.');
    }
    if (this.generateQuiz && this.quizTimed && !(this.quizDuration >= 1 && this.quizDuration <= 1440)) {
      return this.errorMessage.set('Durée du quiz : entre 1 et 1440 minutes.');
    }
    if (this.generateAssignment && this.assignmentTimed && !(this.assignmentDuration >= 1 && this.assignmentDuration <= 1440)) {
      return this.errorMessage.set('Durée du TP : entre 1 et 1440 minutes.');
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.generated.set([]);
    this.startTimer();

    const user = this.authService.currentUserSignal();
    // « URL » (ancien type) suit le choix « Lien web »
    const types = resourceTypes.includes('WEB_PAGE') && this.availableSources().some(s => s.type === 'URL') && !resourceTypes.includes('URL')
      ? [...resourceTypes, 'URL'] : resourceTypes;

    this.aiService.generateAssessments({
      chapterIds,
      generateQuiz: this.generateQuiz,
      generateAssignment: this.generateAssignment,
      quizQuestionCount: this.quizQuestionCount,
      assignmentExerciseCount: this.assignmentExerciseCount,
      difficultyLevels: levels,
      teacherId: user?.id,
      resourceTypes: types,
      dueAt: this.dueIso,
      quizDurationMinutes: this.generateQuiz && this.quizTimed ? this.quizDuration : null,
      assignmentDurationMinutes: this.generateAssignment && this.assignmentTimed ? this.assignmentDuration : null
    }).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.stopTimer();
        const duration = this.formatElapsed(this.elapsed());
        if (res?.success === false) {
          this.errorMessage.set(res.message || "L'IA n'a rien généré.");
          return;
        }
        const items: GeneratedItem[] = [
          ...(res?.generatedQuizzes || []).map((q: any) => ({ kind: 'Quiz' as const, title: q.title, level: q.difficultyLevel, count: q.questionCount })),
          ...(res?.generatedAssignments || []).map((a: any) => ({ kind: 'TP' as const, title: a.title, level: a.difficultyLevel, count: a.exerciseCount }))
        ];
        this.generated.set(items);
        const partial = res?.message && res.message.startsWith('Génération partielle') ? ' ' + res.message : '';
        const notified = this.dueIso ? ' Les étudiants inscrits ont été prévenus de la date limite.' : '';
        this.successMessage.set(`${items.filter(i => i.kind === 'Quiz').length} quiz et ${items.filter(i => i.kind === 'TP').length} TP générés en ${duration}.${partial}${notified}`);
      },
      error: (err) => {
        this.loading.set(false);
        this.stopTimer();
        this.errorMessage.set(err.error?.message || "Erreur lors de la génération par l'IA. Vérifiez que le microservice LMS_AI est actif.");
      }
    });
  }

  formatElapsed(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m} min ${s.toString().padStart(2, '0')} s` : `${s} s`;
  }

  private startTimer(): void {
    this.stopTimer();
    this.elapsed.set(0);
    this.timer = setInterval(() => this.elapsed.update(v => v + 1), 1000);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }
}
