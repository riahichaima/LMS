import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../services/auth.service';
import { LearningService, QuizResult, StudentQuiz } from '../../services/learning.service';
import { AssessmentScheduleService, AttemptState, formatDue, formatDuration } from '../../services/assessment-schedule.service';
import { CountdownComponent } from '../../shared/countdown.component';
import { AttemptGateComponent } from '../../shared/attempt-gate.component';

interface QuestionCorrection {
  question: string;
  correct: boolean;
  student_answer: string;
  expected: string;
  explanation?: string;
}

/**
 * Quiz QCM de l'étudiant : réponses, note, puis correction question par question.
 * Si l'enseignant a fixé une durée : écran « Commencer », chrono, brouillon enregistré
 * et envoi automatique à la fin du temps (un seul passage).
 */
@Component({
  selector: 'app-quiz-player',
  standalone: true,
  imports: [
    CommonModule, FormsModule, AlertComponent, BadgeComponent, ButtonDirective, CardComponent, CardBodyComponent,
    SpinnerComponent, IconDirective, CountdownComponent, AttemptGateComponent
  ],
  templateUrl: './quiz-player.component.html'
})
export class QuizPlayerComponent implements OnInit {
  quiz = signal<StudentQuiz | null>(null);
  result = signal<QuizResult | null>(null);
  attempt = signal<AttemptState | null>(null);
  loading = signal(true);
  starting = signal(false);
  submitting = signal(false);
  autoSubmitted = signal(false);
  error = signal('');
  draftSaved = signal(false);
  // Clé = position de la question (1, 2, ...), valeur = texte de la réponse choisie
  answers: Record<string, string> = {};

  private id = 0;
  private readonly destroyRef = inject(DestroyRef);
  private draftTimer?: ReturnType<typeof setTimeout>;

  readonly formatDue = formatDue;
  readonly formatDuration = formatDuration;

  /** Minuté et en cours : chrono affiché, pas de nouvelle tentative après la correction */
  timed = computed(() => !!this.attempt()?.timed);
  running = computed(() => {
    const a = this.attempt();
    return !!a && a.timed && a.started && !a.submitted && !this.result();
  });
  canRetry = computed(() => !this.timed() && !this.attempt()?.closed);
  showGate = computed(() => {
    const a = this.attempt();
    if (!a || this.result()) return false;
    return (a.timed && !a.started) || (a.closed && !a.started);
  });

  corrections = computed<QuestionCorrection[]>(() => {
    try {
      return JSON.parse(this.result()?.detailedExplanation || '[]');
    } catch {
      return [];
    }
  });

  constructor(
    private route: ActivatedRoute,
    private learningService: LearningService,
    private scheduleService: AssessmentScheduleService,
    private authService: AuthService,
    public location: Location
  ) {
    this.destroyRef.onDestroy(() => clearTimeout(this.draftTimer));
  }

  ngOnInit(): void {
    // Composant réutilisé quand on passe directement d'un quiz à un autre : on recharge à chaque changement d'id
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => this.load(Number(params.get('id'))));
  }

  private load(id: number): void {
    clearTimeout(this.draftTimer);
    this.id = id;
    this.quiz.set(null);
    this.answers = {};
    this.result.set(null);
    this.attempt.set(null);
    this.autoSubmitted.set(false);
    this.draftSaved.set(false);
    this.error.set('');
    this.loading.set(true);
    this.scheduleService.status('QUIZ', this.id).subscribe({
      next: (state) => this.applyState(state),
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger ce quiz.');
      }
    });
  }

  private applyState(state: AttemptState): void {
    this.attempt.set(state);
    // Déjà rendu (minuté) : on affiche directement la note et la correction
    if (state.timed && state.submitted) {
      this.autoSubmitted.set(!!state.autoSubmitted);
      if (state.lastResult) this.result.set({ success: true, ...state.lastResult });
      this.loadQuiz();
      return;
    }
    if (state.timed && state.started) {
      this.answers = this.parseDraft(state.draft);
      // Temps écoulé pendant l'absence de l'étudiant : envoi des réponses enregistrées
      if (state.expired) {
        this.loadQuiz(() => this.submit(true));
        return;
      }
      this.loadQuiz();
      return;
    }
    if (this.showGate()) {
      if (state.closed && state.lastResult) this.result.set({ success: true, ...state.lastResult });
      this.loading.set(false);
      return;
    }
    this.loadQuiz();
  }

  private loadQuiz(then?: () => void): void {
    this.learningService.getQuiz(this.id).subscribe({
      next: (q) => {
        q.questions.sort((a, b) => (a.questionOrder ?? 0) - (b.questionOrder ?? 0));
        q.questions.forEach(question => question.answers.sort((a, b) => (a.answerOrder ?? 0) - (b.answerOrder ?? 0)));
        this.quiz.set(q);
        this.loading.set(false);
        then?.();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger ce quiz.');
      }
    });
  }

  start(): void {
    this.starting.set(true);
    this.error.set('');
    this.scheduleService.start('QUIZ', this.id).subscribe({
      next: (state) => {
        this.starting.set(false);
        this.loading.set(true);
        this.applyState(state);
      },
      error: (err) => {
        this.starting.set(false);
        this.error.set(err.error?.message || 'Impossible de commencer ce quiz.');
      }
    });
  }

  get answeredCount(): number {
    return Object.values(this.answers).filter(Boolean).length;
  }

  /** Brouillon enregistré côté serveur (utilisé si le temps se termine sans validation) */
  onAnswerChange(): void {
    if (!this.running()) return;
    this.draftSaved.set(false);
    clearTimeout(this.draftTimer);
    this.draftTimer = setTimeout(() => {
      this.scheduleService.saveDraft('QUIZ', this.id, JSON.stringify(this.answers)).subscribe({
        next: () => this.draftSaved.set(true),
        error: () => {}
      });
    }, 1200);
  }

  onTimeUp(): void {
    if (!this.result() && !this.submitting()) this.submit(true);
  }

  submit(automatic = false): void {
    const q = this.quiz();
    const user = this.authService.currentUserSignal();
    if (!q || !user || this.submitting()) return;
    clearTimeout(this.draftTimer);
    this.submitting.set(true);
    this.error.set('');
    this.learningService.submitQuiz(q.id, user.id, this.answers).subscribe({
      next: (res) => {
        this.submitting.set(false);
        if (!res.success) {
          this.error.set("La correction n'a pas abouti. Vérifiez que le service IA est démarré.");
          return;
        }
        this.autoSubmitted.set(automatic);
        this.result.set(res);
        this.attempt.update(a => (a ? { ...a, submitted: true } : a));
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err.error?.message || 'Erreur lors de la soumission du quiz.');
      }
    });
  }

  retry(): void {
    this.answers = {};
    this.result.set(null);
  }

  private parseDraft(draft: string | null | undefined): Record<string, string> {
    try {
      const parsed = JSON.parse(draft || '{}');
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  }
}
