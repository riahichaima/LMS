import { ScheduleEditorComponent } from '../../../shared/schedule-editor.component';
import { Component, OnInit, computed, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AlertComponent, BadgeComponent, FormSelectDirective, SpinnerComponent } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AiService, CourseAssessments, ReviewAssignment, ReviewQuiz } from '../../../services/ai.service';
import { ManualAssessmentService } from '../../../services/manual-assessment.service';
import { AssessmentFileService } from '../../../services/assessment-file.service';
import { LEVEL_COLORS, LEVEL_LABELS, TpExercise, splitExercises } from '../exercise-utils';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';

// Un quiz ou un TP accompagné du cours et du chapitre auxquels il appartient
interface QuizItem {
  courseTitle: string;
  chapterTitle: string;
  quiz: ReviewQuiz;
}

interface TpItem {
  courseTitle: string;
  chapterTitle: string;
  tp: ReviewAssignment;
  exercises: TpExercise[];
}

export type SourceFilter = '' | 'ai' | 'manual';

/**
 * Onglet « Mes quiz & TP » de la page Quiz & TP : statistiques, quiz, TP et corrections par niveau,
 * avec la distinction entre contenus générés par l'IA et créés par l'enseignant.
 */
@Component({
  selector: 'app-teacher-ai-content',
  standalone: true,
  imports: [CommonModule, FormsModule, BadgeComponent, AlertComponent, SpinnerComponent, FormSelectDirective, IconDirective,
    StatCardComponent, EmptyStateComponent, ScheduleEditorComponent],
  templateUrl: './teacher-ai-content.component.html'
})
export class TeacherAiContentComponent implements OnInit {
  /** Demande à la page parente d'ouvrir un autre onglet (générer / créer). */
  navigate = output<'generate' | 'files'>();

  readonly levels = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

  courses = signal<CourseAssessments[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  busyId = signal<string | null>(null);

  tab = signal<'quiz' | 'tp' | 'corrections'>('quiz');
  // Filtres : 0 = tous les cours, '' = tous les niveaux / toutes les origines
  courseFilter = signal(0);
  levelFilter = signal('');
  sourceFilter = signal<SourceFilter>('');
  /** Sujets importés en fichier et copies à corriger (onglet « Importer un fichier ») */
  importedFiles = signal(0);
  copiesToGrade = signal(0);

  private filteredCourses = computed(() => {
    const id = this.courseFilter();
    return id ? this.courses().filter(c => c.courseId === id) : this.courses();
  });

  private matches(item: { difficultyLevel: string; generatedByAi?: boolean }): boolean {
    const source = this.sourceFilter();
    if (this.levelFilter() && item.difficultyLevel !== this.levelFilter()) return false;
    if (source === 'ai' && item.generatedByAi === false) return false;
    if (source === 'manual' && item.generatedByAi !== false) return false;
    return true;
  }

  quizzes = computed<QuizItem[]>(() =>
    this.filteredCourses().flatMap(c => c.chapters.flatMap(ch =>
      ch.quizzes.filter(q => this.matches(q))
        .map(quiz => ({ courseTitle: c.courseTitle, chapterTitle: ch.chapterTitle, quiz }))
    ))
  );

  tps = computed<TpItem[]>(() =>
    this.filteredCourses().flatMap(c => c.chapters.flatMap(ch =>
      ch.assignments.filter(a => this.matches(a))
        .map(tp => ({ courseTitle: c.courseTitle, chapterTitle: ch.chapterTitle, tp, exercises: splitExercises(tp.statement, tp.expectedSolution) }))
    ))
  );

  exerciseCount = computed(() => this.tps().reduce((sum, t) => sum + t.exercises.length, 0));

  /** Statistiques sur l'ensemble (avant filtres) */
  readonly stats = computed(() => {
    const allQuizzes = this.courses().flatMap(c => c.chapters.flatMap(ch => ch.quizzes));
    const allTps = this.courses().flatMap(c => c.chapters.flatMap(ch => ch.assignments));
    const all = [...allQuizzes, ...allTps];
    const byLevel = (l: string) => all.filter(x => x.difficultyLevel === l).length;
    return {
      quizzes: allQuizzes.length,
      questions: allQuizzes.reduce((n, q) => n + q.questions.length, 0),
      tps: allTps.length,
      ai: all.filter(x => x.generatedByAi !== false).length,
      manual: all.filter(x => x.generatedByAi === false).length,
      levels: this.levels.map(l => ({ level: l, count: byLevel(l), percent: all.length ? Math.round(byLevel(l) * 100 / all.length) : 0 })),
      courses: this.courses().map(c => ({
        title: c.courseTitle,
        quizzes: c.chapters.reduce((n, ch) => n + ch.quizzes.length, 0),
        tps: c.chapters.reduce((n, ch) => n + ch.assignments.length, 0)
      }))
    };
  });

  constructor(private aiService: AiService, private manualService: ManualAssessmentService, private fileService: AssessmentFileService) {}

  ngOnInit(): void {
    this.load();
    this.fileService.teacherFiles().subscribe({
      next: files => {
        this.importedFiles.set(files.length);
        this.copiesToGrade.set(files.reduce((n, f) => n + f.submissionCount - f.gradedCount, 0));
      }
    });
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.aiService.getTeacherAssessmentsOverview().subscribe({
      next: (data) => {
        this.loading.set(false);
        this.courses.set(data);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger vos quiz et TP. Vérifiez que le backend est démarré.');
      }
    });
  }

  deleteQuiz(quiz: ReviewQuiz): void {
    if (!confirm(`Supprimer le quiz « ${quiz.title} » ?`)) return;
    this.busyId.set('q' + quiz.id);
    this.manualService.deleteQuiz(quiz.id).subscribe({
      next: () => { this.busyId.set(null); this.successMessage.set('Quiz supprimé.'); this.load(); },
      error: (err) => { this.busyId.set(null); this.errorMessage.set(err.error?.message || 'Suppression impossible.'); }
    });
  }

  deleteTp(tp: ReviewAssignment): void {
    if (!confirm(`Supprimer le TP « ${tp.title} » ?`)) return;
    this.busyId.set('t' + tp.id);
    this.manualService.deleteAssignment(tp.id).subscribe({
      next: () => { this.busyId.set(null); this.successMessage.set('TP supprimé.'); this.load(); },
      error: (err) => { this.busyId.set(null); this.errorMessage.set(err.error?.message || 'Suppression impossible.'); }
    });
  }

  levelLabel(level: string): string {
    return LEVEL_LABELS[level] || level;
  }

  levelColor(level: string): string {
    return LEVEL_COLORS[level] || 'secondary';
  }

  letter(index: number): string {
    return 'ABCDEFGH'[index] || '?';
  }

  correctLetter(quiz: ReviewQuiz, questionIndex: number): string {
    const answers = quiz.questions[questionIndex].answers;
    const i = answers.findIndex(a => a.isCorrect);
    return i >= 0 ? this.letter(i) : '?';
  }

  correctText(quiz: ReviewQuiz, questionIndex: number): string {
    return quiz.questions[questionIndex].answers.find(a => a.isCorrect)?.answerText
      || quiz.questions[questionIndex].correctAnswer;
  }
}
