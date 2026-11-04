import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  SpinnerComponent,
  AlertComponent,
  ProgressBarComponent,
  ProgressComponent,
  ModalComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  ModalBodyComponent
} from '@coreui/angular';
import { CourseService, Enrollment } from '../../services/course.service';
import { FeedbackService } from '../../services/feedback.service';
import { CourseFeedbackComponent } from '../../shared/course-feedback.component';
import { StarRatingComponent } from '../../shared/star-rating.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

@Component({
  selector: 'app-my-enrollments',
  standalone: true,
  imports: [
    CommonModule,
    SpinnerComponent,
    AlertComponent,
    ProgressBarComponent,
    ProgressComponent,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    RouterLink,
    IconDirective,
    CourseFeedbackComponent,
    StarRatingComponent,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './my-enrollments.component.html',
  styleUrl: './my-enrollments.component.scss'
})
export class MyEnrollmentsComponent implements OnInit {
  enrollments = signal<Enrollment[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  // Note moyenne de chaque cours : courseId → { average, count }
  ratings = signal<Map<number, { average: number; count: number }>>(new Map());
  // Cours dont la fenêtre « Donner mon avis » est ouverte
  feedbackFor = signal<Enrollment | null>(null);

  // Onglets : toutes, en cours, terminées (filtre d'affichage uniquement)
  tab = signal<'all' | 'progress' | 'done'>('all');
  readonly completedCount = computed(() => this.enrollments().filter(e => this.isDone(e)).length);
  readonly visibleEnrollments = computed(() => {
    const tab = this.tab();
    return this.enrollments().filter(e => tab === 'all' || (tab === 'done') === this.isDone(e));
  });

  isDone(e: Enrollment): boolean {
    return !!(e.isCompleted || e.completed);
  }

  constructor(private courseService: CourseService, private feedbackService: FeedbackService) {}

  ngOnInit(): void {
    this.loadEnrollments();
    this.loadRatings();
  }

  loadEnrollments(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.courseService.getMyEnrollments().subscribe({
      next: (data) => {
        this.enrollments.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger vos inscriptions. Vérifiez que le backend est démarré.');
      }
    });
  }

  openFeedback(enrollment: Enrollment): void {
    this.feedbackFor.set(enrollment);
  }

  /** Fermeture de la fenêtre : la note moyenne affichée sur les cartes est rafraîchie. */
  closeFeedback(): void {
    this.feedbackFor.set(null);
    this.loadRatings();
  }

  private loadRatings(): void {
    this.feedbackService.catalogRatings().subscribe({
      next: (list) => this.ratings.set(new Map(list.map(r => [r.courseId, { average: r.average, count: r.count }])))
    });
  }
}
