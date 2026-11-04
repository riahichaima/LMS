import { Component, OnInit, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FeedbackService, RatingSummary } from '../services/feedback.service';
import { StarRatingComponent } from './star-rating.component';

/** Note du cours dans la page du cours : moyenne, répartition et note de l'étudiant (1 à 5 étoiles + avis). */
@Component({
  selector: 'app-course-rating',
  standalone: true,
  imports: [CommonModule, FormsModule, StarRatingComponent],
  template: `
    <div class="rating-card">
      <div class="d-flex justify-content-between align-items-start gap-2">
        <strong>Avis sur ce cours</strong>
        @if (summary(); as s) {
          @if (s.count > 0) {
            <span class="small text-body-secondary text-end">{{ s.average }}/5 · {{ s.count }} avis · {{ s.satisfactionPercent }} % satisfaits</span>
          } @else {
            <span class="small text-body-secondary">Pas encore noté</span>
          }
        }
      </div>

      @if (summary(); as s) {
        @if (s.count > 0) {
          <div class="d-flex align-items-center gap-2 mt-1">
            <app-star-rating [value]="s.average" [size]="18" />
          </div>
          <div class="dist mt-2">
            @for (n of [5, 4, 3, 2, 1]; track n) {
              <div class="dist-row">
                <span>{{ n }}★</span>
                <div class="dist-track"><div class="dist-bar" [style.width.%]="pct(s, n)"></div></div>
                <span class="dist-count">{{ s.distribution[n - 1] }}</span>
              </div>
            }
          </div>
        }

        @if (s.canRate) {
          <div class="mine mt-3">
            <div class="small fw-semibold mb-1">{{ s.myRating ? 'Votre note' : 'Notez ce cours' }}</div>
            <app-star-rating [value]="stars()" [editable]="true" [size]="26" (pick)="choose($event)" />
            @if (stars()) {
              <textarea class="form-control form-control-sm mt-2" rows="2" maxlength="1000" placeholder="Votre avis (facultatif)…"
                        [ngModel]="comment()" (ngModelChange)="comment.set($event)" aria-label="Votre avis"></textarea>
              <div class="d-flex gap-2 mt-2">
                <button type="button" class="btn btn-sm btn-primary" (click)="save()" [disabled]="saving()">
                  {{ saving() ? 'Enregistrement…' : (s.myRating ? 'Mettre à jour' : 'Publier mon avis') }}
                </button>
                @if (s.myRating) {
                  <button type="button" class="btn btn-sm btn-outline-secondary" (click)="remove()" [disabled]="saving()">Retirer</button>
                }
              </div>
            }
            @if (saved()) { <div class="small text-success mt-1">{{ saved() }}</div> }
            @if (error()) { <div class="small text-danger mt-1">{{ error() }}</div> }
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .rating-card { padding: 14px 16px; border: 1px solid var(--cui-border-color); border-radius: 10px; background: var(--cui-body-bg); }
    .dist-row { display: grid; grid-template-columns: 2rem 1fr 1.5rem; align-items: center; gap: 6px; font-size: .75rem; color: var(--cui-secondary-color); }
    .dist-track { height: 6px; border-radius: 3px; background: var(--cui-tertiary-bg); overflow: hidden; }
    .dist-bar { height: 100%; background: #f5a524; border-radius: 3px; }
    .dist-count { text-align: right; }
    .mine { border-top: 1px solid var(--cui-border-color); padding-top: 12px; }
  `]
})
export class CourseRatingComponent implements OnInit {
  courseId = input.required<number>();

  summary = signal<RatingSummary | null>(null);
  stars = signal<number | null>(null);
  comment = signal('');
  saving = signal(false);
  saved = signal('');
  error = signal('');

  constructor(private feedback: FeedbackService) {}

  ngOnInit(): void {
    this.feedback.getRating(this.courseId()).subscribe({ next: s => this.apply(s), error: () => {} });
  }

  choose(n: number): void {
    this.stars.set(n);
    this.saved.set('');
  }

  save(): void {
    const n = this.stars();
    if (!n) return;
    this.saving.set(true);
    this.error.set('');
    this.feedback.rate(this.courseId(), n, this.comment().trim() || null).subscribe({
      next: s => {
        this.saving.set(false);
        this.apply(s);
        this.saved.set('Merci ! Votre avis a été transmis au formateur.');
      },
      error: err => {
        this.saving.set(false);
        this.error.set(err.error?.message || "Votre avis n'a pas pu être enregistré.");
      }
    });
  }

  remove(): void {
    this.saving.set(true);
    this.feedback.deleteRating(this.courseId()).subscribe({
      next: s => {
        this.saving.set(false);
        this.apply(s);
        this.saved.set('Votre note a été retirée.');
      },
      error: () => this.saving.set(false)
    });
  }

  pct(s: RatingSummary, n: number): number {
    return s.count ? Math.round(s.distribution[n - 1] * 100 / s.count) : 0;
  }

  private apply(s: RatingSummary): void {
    this.summary.set(s);
    this.stars.set(s.myRating?.stars ?? null);
    this.comment.set(s.myRating?.comment ?? '');
  }
}
