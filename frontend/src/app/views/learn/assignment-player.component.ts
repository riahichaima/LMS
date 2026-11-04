import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AssignmentSubmission, LearningService, StudentAssignment } from '../../services/learning.service';
import { AssessmentScheduleService, AttemptState, formatDue, formatDuration } from '../../services/assessment-schedule.service';
import { MarkdownPipe } from '../../shared/markdown.pipe';
import { CountdownComponent } from '../../shared/countdown.component';
import { AttemptGateComponent } from '../../shared/attempt-gate.component';

/**
 * TP de l'étudiant : énoncés complets (exemples, code de départ), réponse (texte ou code),
 * correction par l'IA, puis solution de référence visible après la soumission.
 * Si l'enseignant a fixé une durée : écran « Commencer », chrono, brouillon enregistré
 * et envoi automatique à la fin du temps (un seul passage).
 */
@Component({
  selector: 'app-assignment-player',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonDirective, CardComponent,
    CardBodyComponent, CardHeaderComponent, SpinnerComponent, IconDirective, MarkdownPipe, CountdownComponent,
    AttemptGateComponent
  ],
  templateUrl: './assignment-player.component.html',
  styleUrl: './assignment-player.component.scss'
})
export class AssignmentPlayerComponent implements OnInit {
  assignment = signal<StudentAssignment | null>(null);
  result = signal<AssignmentSubmission | null>(null);
  attempt = signal<AttemptState | null>(null);
  loading = signal(true);
  starting = signal(false);
  submitting = signal(false);
  autoSubmitted = signal(false);
  draftSaved = signal(false);
  error = signal('');
  answer = '';

  private id = 0;
  private readonly destroyRef = inject(DestroyRef);
  private draftTimer?: ReturnType<typeof setTimeout>;

  readonly formatDue = formatDue;
  readonly formatDuration = formatDuration;

  running = computed(() => {
    const a = this.attempt();
    return !!a && a.timed && a.started && !a.submitted && !this.result();
  });
  canRetry = computed(() => !this.attempt()?.timed && !this.attempt()?.closed);
  showGate = computed(() => {
    const a = this.attempt();
    if (!a || this.result()) return false;
    return (a.timed && !a.started) || (a.closed && !a.started && !this.assignment()?.lastSubmission);
  });

  constructor(
    private route: ActivatedRoute,
    private learningService: LearningService,
    private scheduleService: AssessmentScheduleService
  ) {
    this.destroyRef.onDestroy(() => clearTimeout(this.draftTimer));
  }

  ngOnInit(): void {
    // Composant réutilisé quand on passe directement d'un TP à un autre : on recharge à chaque changement d'id
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => this.load(Number(params.get('id'))));
  }

  private load(id: number): void {
    clearTimeout(this.draftTimer);
    this.id = id;
    this.assignment.set(null);
    this.answer = '';
    this.result.set(null);
    this.attempt.set(null);
    this.autoSubmitted.set(false);
    this.draftSaved.set(false);
    this.error.set('');
    this.loading.set(true);
    this.scheduleService.status('TP', this.id).subscribe({
      next: (state) => this.applyState(state),
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger ce TP.');
      }
    });
  }

  private applyState(state: AttemptState): void {
    this.attempt.set(state);
    if (state.timed && !state.started) {
      this.loading.set(false);
      return;
    }
    this.loadAssignment(state);
  }

  private loadAssignment(state: AttemptState): void {
    this.learningService.getAssignment(this.id).subscribe({
      next: (a) => {
        this.assignment.set(a);
        this.loading.set(false);
        if (a.lastSubmission) {
          this.result.set(a.lastSubmission);
          this.answer = a.lastSubmission.answer;
          this.autoSubmitted.set(!!state.autoSubmitted);
          return;
        }
        this.answer = state.draft ?? this.starterCode(a.description);
        // Temps écoulé pendant l'absence de l'étudiant : envoi de la réponse enregistrée
        if (state.timed && state.started && !state.submitted && state.expired) this.submit(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger ce TP.');
      }
    });
  }

  start(): void {
    this.starting.set(true);
    this.error.set('');
    this.scheduleService.start('TP', this.id).subscribe({
      next: (state) => {
        this.starting.set(false);
        this.loading.set(true);
        this.applyState(state);
      },
      error: (err) => {
        this.starting.set(false);
        this.error.set(err.error?.message || 'Impossible de commencer ce TP.');
      }
    });
  }

  /** Brouillon enregistré côté serveur (utilisé si le temps se termine sans validation) */
  onAnswerChange(): void {
    if (!this.running()) return;
    this.draftSaved.set(false);
    clearTimeout(this.draftTimer);
    this.draftTimer = setTimeout(() => {
      this.scheduleService.saveDraft('TP', this.id, this.answer).subscribe({
        next: () => this.draftSaved.set(true),
        error: () => {}
      });
    }, 1500);
  }

  onTimeUp(): void {
    if (!this.result() && !this.submitting()) this.submit(true);
  }

  submit(automatic = false): void {
    const a = this.assignment();
    if (!a || this.submitting()) return;
    // Envoi automatique : même une réponse vide est rendue (le serveur la note)
    const answer = this.answer.trim() ? this.answer : (automatic ? '(Aucune réponse rendue avant la fin du temps)' : '');
    if (!answer) return;
    clearTimeout(this.draftTimer);
    this.submitting.set(true);
    this.error.set('');
    this.learningService.submitAssignment(a.id, answer).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.autoSubmitted.set(automatic);
        this.result.set(res);
        this.attempt.update(s => (s ? { ...s, submitted: true } : s));
        setTimeout(() => document.getElementById('correction')?.scrollIntoView({ behavior: 'smooth' }));
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err.error?.message || "La correction par l'IA a échoué. Réessayez.");
      }
    });
  }

  retry(): void {
    this.result.set(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  onTab(event: KeyboardEvent): void {
    // Tabulation dans la zone de code au lieu de changer de champ
    if (event.key !== 'Tab') return;
    event.preventDefault();
    const el = event.target as HTMLTextAreaElement;
    const { selectionStart: start, selectionEnd: end } = el;
    this.answer = this.answer.slice(0, start) + '    ' + this.answer.slice(end);
    setTimeout(() => el.setSelectionRange(start + 4, start + 4));
    this.onAnswerChange();
  }

  // Pré-remplit la réponse avec les codes de départ des exercices
  private starterCode(description: string): string {
    const blocks: string[] = [];
    const sections = (description || '').split(/^### /m).filter(s => s.trim());
    sections.forEach((section, i) => {
      const starter = section.match(/Code de départ :\s*```[\w+-]*\n([\s\S]*?)```/);
      if (starter) blocks.push(`# Exercice ${i + 1}\n${starter[1].trimEnd()}`);
    });
    return blocks.join('\n\n');
  }
}
