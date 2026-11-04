import { Component, OnInit, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FeedbackService, RatingSummary } from '../services/feedback.service';
import { CourseRatingComponent } from './course-rating.component';
import { FeedbackThreadComponent } from './feedback-thread.component';
import { TeacherRatingComponent } from './teacher-rating.component';

/**
 * « Votre avis sur ce cours » : note en étoiles, j'aime / je n'aime pas et commentaires du cours,
 * évaluation du formateur en %.
 *  - mode "bar"  : bandeau compact en haut de la page du cours (les formulaires s'ouvrent au clic) ;
 *  - mode "full" : tout est affiché (fenêtre « Donner mon avis » de Mes inscriptions).
 */
@Component({
  selector: 'app-course-feedback',
  standalone: true,
  imports: [CommonModule, CourseRatingComponent, FeedbackThreadComponent, TeacherRatingComponent],
  template: `
    @if (mode() === 'bar') {
      <div class="cf-card">
        <div class="cf-head">
          <div>
            <strong>Votre avis sur ce cours</strong>
            <div class="small text-body-secondary">Notez le cours, réagissez, commentez et évaluez le formateur.</div>
          </div>
          <div class="cf-buttons">
            <button type="button" class="btn btn-sm" [class.btn-warning]="section() === 'rating'" [class.btn-outline-warning]="section() !== 'rating'"
                    (click)="toggle('rating')" [attr.aria-expanded]="section() === 'rating'">
              ★ Noter le cours
              @if (rating(); as r) { @if (r.count > 0) { <span class="ms-1 fw-bold">{{ r.average }}/5</span> } }
            </button>
            @if (teacherId()) {
              <button type="button" class="btn btn-sm" [class.btn-success]="section() === 'teacher'" [class.btn-outline-success]="section() !== 'teacher'"
                      (click)="toggle('teacher')" [attr.aria-expanded]="section() === 'teacher'">
                🎯 Évaluer le formateur (%)
              </button>
            }
          </div>
        </div>

        @if (section() === 'rating') {
          <app-course-rating class="d-block mt-3" [courseId]="courseId()" />
        }
        @if (section() === 'teacher' && teacherId()) {
          <app-teacher-rating class="d-block mt-3" [teacherId]="teacherId()!" [teacherName]="teacherName()" />
        }

        <app-feedback-thread kind="course" [targetId]="courseId()" />
      </div>
    } @else {
      <div class="cf-grid">
        <app-course-rating [courseId]="courseId()" />
        @if (teacherId()) {
          <app-teacher-rating [teacherId]="teacherId()!" [teacherName]="teacherName()" />
        }
      </div>
      <div class="mt-3">
        <strong class="small text-uppercase text-body-secondary">J'aime, je n'aime pas et commentaires du cours</strong>
        <app-feedback-thread kind="course" [targetId]="courseId()" [startOpen]="true" />
      </div>
    }
  `,
  styles: [`
    .cf-card { padding: 14px 16px; border: 1px solid var(--cui-border-color); border-left: 4px solid #f5a524; border-radius: 10px;
               background: var(--cui-body-bg); }
    .cf-head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px; }
    .cf-buttons { display: flex; flex-wrap: wrap; gap: 8px; }
    .cf-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; }
  `]
})
export class CourseFeedbackComponent implements OnInit {
  courseId = input.required<number>();
  teacherId = input<number | null | undefined>(null);
  teacherName = input<string | null | undefined>(null);
  mode = input<'bar' | 'full'>('bar');

  readonly section = signal<'rating' | 'teacher' | null>(null);
  readonly rating = signal<RatingSummary | null>(null);

  constructor(private feedback: FeedbackService) {}

  ngOnInit(): void {
    if (this.mode() === 'bar') {
      this.feedback.getRating(this.courseId()).subscribe({ next: r => this.rating.set(r), error: () => {} });
    }
  }

  toggle(section: 'rating' | 'teacher'): void {
    this.section.update(s => (s === section ? null : section));
  }
}
