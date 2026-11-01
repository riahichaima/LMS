import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  SpinnerComponent,
  AlertComponent,
  ProgressBarComponent,
  ProgressComponent,
  ButtonDirective,
  ModalComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  ModalBodyComponent
} from '@coreui/angular';
import { CourseService, Enrollment } from '../../services/course.service';
import { FeedbackService } from '../../services/feedback.service';
import { CourseFeedbackComponent } from '../../shared/course-feedback.component';
import { StarRatingComponent } from '../../shared/star-rating.component';

@Component({
  selector: 'app-my-enrollments',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    SpinnerComponent,
    AlertComponent,
    ProgressBarComponent,
    ProgressComponent,
    ButtonDirective,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    RouterLink,
    IconDirective,
    CourseFeedbackComponent,
    StarRatingComponent
  ],
  templateUrl: './my-enrollments.component.html'
})
export class MyEnrollmentsComponent implements OnInit {
  enrollments = signal<Enrollment[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  // Note moyenne de chaque cours : courseId → { average, count }
  ratings = signal<Map<number, { average: number; count: number }>>(new Map());
  // Cours dont la fenêtre « Donner mon avis » est ouverte
  feedbackFor = signal<Enrollment | null>(null);

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
