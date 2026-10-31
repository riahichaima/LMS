import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent,
  FormCheckComponent,
  FormCheckInputDirective,
  FormCheckLabelDirective,
  FormControlDirective,
  FormSelectDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { CourseService, Course, Chapter } from '../../../services/course.service';
import { AiService, AiModelStatus } from '../../../services/ai.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-teacher-assessments',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent,
    FormCheckComponent,
    FormCheckInputDirective,
    FormCheckLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    IconDirective
  ],
  templateUrl: './teacher-assessments.component.html'
})
export class TeacherAssessmentsComponent implements OnInit {
  private destroyRef = inject(DestroyRef);

  courses = signal<Course[]>([]);
  selectedCourseId = signal<number | undefined>(undefined);
  chapters = signal<Chapter[]>([]);
  selectedChapterId = signal<number | undefined>(undefined);

  generateQuiz = true;
  generateAssignment = true;
  quizQuestionCount = 5;
  assignmentExerciseCount = 3;

  // Azure génère tous les niveaux en parallèle : les trois sont cochés par défaut
  beginner = true;
  intermediate = true;
  advanced = true;

  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  // Secondes écoulées depuis le lancement de la génération
  elapsed = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  modelStatus = signal<AiModelStatus | null>(null);

  // Sources disponibles dans le chapitre sélectionné (PDF, vidéo, lien...) avec leur nombre
  private readonly sourceLabels: Record<string, string> = {
    PDF: 'PDF', VIDEO: 'Vidéo', AUDIO: 'Audio', YOUTUBE_VIDEO: 'YouTube', WEB_PAGE: 'Lien web', TEXT: 'Texte'
  };
  availableSources = computed(() => {
    const chapter = this.chapters().find(c => c.id === this.selectedChapterId());
    const counts = new Map<string, number>();
    for (const lesson of chapter?.lessons || []) {
      for (const r of lesson.resources || []) {
        counts.set(r.resourceType, (counts.get(r.resourceType) || 0) + 1);
      }
    }
    return Object.keys(this.sourceLabels)
      .filter(type => counts.has(type))
      .map(type => ({ type, label: this.sourceLabels[type], count: counts.get(type)! }));
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
    this.selectedChapterId.set(undefined);
    this.excludedSources.set([]);
    if (!courseId) return;
    this.courseService.getCourseChapters(courseId).subscribe({
      next: (chaps) => {
        this.chapters.set(chaps);
        if (chaps.length > 0) {
          this.selectedChapterId.set(chaps[0].id);
        }
      }
    });
  }

  onGenerate(): void {
    const chapterId = this.selectedChapterId();
    if (!chapterId) {
      this.errorMessage.set('Veuillez sélectionner un chapitre.');
      return;
    }

    const levels: string[] = [];
    if (this.beginner) levels.push('BEGINNER');
    if (this.intermediate) levels.push('INTERMEDIATE');
    if (this.advanced) levels.push('ADVANCED');

    if (levels.length === 0) {
      this.errorMessage.set('Veuillez cocher au moins un niveau de difficulté.');
      return;
    }
    if (!this.generateQuiz && !this.generateAssignment) {
      this.errorMessage.set('Cochez au moins "Quiz" ou "TP".');
      return;
    }
    const resourceTypes = this.selectedSourceTypes();
    if (this.availableSources().length > 0 && resourceTypes.length === 0) {
      this.errorMessage.set('Cochez au moins une source (PDF, vidéo, lien...).');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.startTimer();

    const user = this.authService.currentUserSignal();

    this.aiService.generateAssessments({
      chapterIds: [chapterId],
      generateQuiz: this.generateQuiz,
      generateAssignment: this.generateAssignment,
      quizQuestionCount: this.quizQuestionCount,
      assignmentExerciseCount: this.assignmentExerciseCount,
      difficultyLevels: levels,
      teacherId: user?.id,
      resourceTypes
    }).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.stopTimer();
        const duration = this.formatElapsed(this.elapsed());
        if (res?.success === false) {
          this.errorMessage.set(res.message || "L'IA n'a rien généré.");
        } else {
          const quizzes = res?.generatedQuizzes?.length || 0;
          const tps = res?.generatedAssignments?.length || 0;
          const partial = res?.message && res.message.startsWith('Génération partielle') ? ' ' + res.message : '';
          this.successMessage.set(`${quizzes} quiz et ${tps} TP générés en ${duration}.${partial}`);
        }
      },
      error: () => {
        this.loading.set(false);
        this.stopTimer();
        this.errorMessage.set("Erreur lors de la génération par l'IA. Vérifiez que le microservice LMS_AI est actif.");
      }
    });
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

  // Nombre d'appels au modèle pour la demande en cours (affiché comme estimation)
  get callCount(): number {
    const levels = [this.beginner, this.intermediate, this.advanced].filter(Boolean).length;
    return levels * ((this.generateQuiz ? 1 : 0) + (this.generateAssignment ? 1 : 0));
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
