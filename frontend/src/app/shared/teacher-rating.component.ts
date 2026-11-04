import { Component, OnInit, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FeedbackService, TeacherRatingSummary } from '../services/feedback.service';
import { AuthService } from '../services/auth.service';

/** Évaluation d'un formateur en pourcentage (0 à 100 %) : moyenne des étudiants + mon évaluation. */
@Component({
  selector: 'app-teacher-rating',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="tr-card">
      <div class="d-flex justify-content-between align-items-start gap-2 flex-wrap">
        <strong>Évaluation du formateur{{ teacherName() ? ' · ' + teacherName() : '' }}</strong>
        @if (summary(); as s) {
          <span class="small text-body-secondary">
            {{ s.count > 0 ? (s.average | number:'1.0-1') + ' % · ' + s.count + ' évaluation' + (s.count > 1 ? 's' : '') : 'Pas encore évalué' }}
          </span>
        }
      </div>

      @if (summary(); as s) {
        @if (s.count > 0) {
          <div class="tr-track mt-2" role="img" [attr.aria-label]="'Moyenne ' + s.average + ' %'">
            <div class="tr-bar" [style.width.%]="s.average"></div>
          </div>
        }

        @if (s.canRate) {
          <div class="mine mt-3">
            <label class="small fw-semibold d-flex justify-content-between" [for]="'pct-' + teacherId()">
              <span>{{ s.myRating ? 'Votre évaluation' : 'Évaluez ce formateur' }}</span>
              <span class="pct">{{ value() !== null ? value() + ' %' : '— %' }}</span>
            </label>
            <input type="range" class="form-range" min="0" max="100" step="5" [id]="'pct-' + teacherId()"
                   [value]="value() ?? 50" (input)="choose($any($event.target).valueAsNumber)"
                   aria-label="Évaluation du formateur en pourcentage">
            <div class="d-flex justify-content-between small text-body-secondary">
              <span>0 %</span><span>{{ label() }}</span><span>100 %</span>
            </div>
            @if (value() !== null) {
              <textarea class="form-control form-control-sm mt-2" rows="2" maxlength="1000" placeholder="Un mot pour le formateur (facultatif)…"
                        [ngModel]="comment()" (ngModelChange)="comment.set($event)" aria-label="Commentaire pour le formateur"></textarea>
              <div class="d-flex gap-2 mt-2">
                <button type="button" class="btn btn-sm btn-primary" (click)="save()" [disabled]="saving()">
                  {{ saving() ? 'Enregistrement…' : (s.myRating ? 'Mettre à jour' : 'Envoyer mon évaluation') }}
                </button>
                @if (s.myRating) {
                  <button type="button" class="btn btn-sm btn-outline-secondary" (click)="remove()" [disabled]="saving()">Retirer</button>
                }
              </div>
            }
            @if (saved()) { <div class="small text-success mt-1">{{ saved() }}</div> }
          </div>
        } @else if (isStudent) {
          <div class="small text-body-secondary mt-2">Inscrivez-vous à un cours de ce formateur pour pouvoir l'évaluer.</div>
        }
        @if (error()) { <div class="small text-danger mt-1">{{ error() }}</div> }
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .tr-card { padding: 14px 16px; border: 1px solid var(--cui-border-color); border-radius: 10px; background: var(--cui-body-bg); }
    .tr-track { height: 8px; border-radius: 4px; background: var(--cui-tertiary-bg); overflow: hidden; }
    .tr-bar { height: 100%; border-radius: 4px; background: var(--cui-success); }
    .pct { font-size: 1.1rem; color: var(--cui-primary); }
    .mine { border-top: 1px solid var(--cui-border-color); padding-top: 12px; }
  `]
})
export class TeacherRatingComponent implements OnInit {
  teacherId = input.required<number>();
  teacherName = input<string | null | undefined>(null);

  summary = signal<TeacherRatingSummary | null>(null);
  value = signal<number | null>(null);
  comment = signal('');
  saving = signal(false);
  saved = signal('');
  error = signal('');
  readonly isStudent: boolean;

  readonly label = computed(() => {
    const v = this.value();
    if (v === null) return 'Glissez pour évaluer';
    return v >= 85 ? 'Excellent' : v >= 70 ? 'Très bien' : v >= 50 ? 'Bien' : v >= 30 ? 'Moyen' : 'Insuffisant';
  });

  constructor(private feedback: FeedbackService, authService: AuthService) {
    this.isStudent = authService.isStudent();
  }

  ngOnInit(): void {
    this.feedback.teacherRating(this.teacherId()).subscribe({ next: s => this.apply(s), error: () => {} });
  }

  choose(v: number): void {
    this.value.set(v);
    this.saved.set('');
  }

  save(): void {
    const v = this.value();
    if (v === null) return;
    this.saving.set(true);
    this.error.set('');
    this.feedback.rateTeacher(this.teacherId(), v, this.comment().trim() || null).subscribe({
      next: s => { this.saving.set(false); this.apply(s); this.saved.set('Merci ! Votre évaluation a été transmise au formateur.'); },
      error: err => { this.saving.set(false); this.error.set(err.error?.message || "Votre évaluation n'a pas pu être enregistrée."); }
    });
  }

  remove(): void {
    this.saving.set(true);
    this.feedback.deleteTeacherRating(this.teacherId()).subscribe({
      next: s => { this.saving.set(false); this.apply(s); this.saved.set('Votre évaluation a été retirée.'); },
      error: () => this.saving.set(false)
    });
  }

  private apply(s: TeacherRatingSummary): void {
    this.summary.set(s);
    this.value.set(s.myRating?.percent ?? null);
    this.comment.set(s.myRating?.comment ?? '');
  }
}
