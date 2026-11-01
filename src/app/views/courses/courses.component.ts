import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  SpinnerComponent,
  AlertComponent,
  BadgeComponent,
  ButtonDirective
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { CourseService, Course } from '../../services/course.service';
import { PaymentService } from '../../services/payment.service';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { StarRatingComponent } from '../../shared/star-rating.component';

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    StarRatingComponent,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    SpinnerComponent,
    AlertComponent,
    BadgeComponent,
    ButtonDirective,
    IconDirective
  ],
  templateUrl: './courses.component.html'
})
export class CoursesComponent implements OnInit {
  courses = signal<Course[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  // Cours auxquels l'étudiant a déjà accès (inscrit / acheté)
  enrolledIds = signal<Set<number>>(new Set());
  // Cours en cours de traitement (inscription ou redirection vers Stripe)
  busyId = signal<number | null>(null);
  paymentsEnabled = signal(true);
  // Note moyenne de chaque cours : courseId → { average, count }
  ratings = signal<Map<number, { average: number; count: number }>>(new Map());
  readonly isStudent: boolean;

  constructor(
    private courseService: CourseService,
    private paymentService: PaymentService,
    private feedbackService: FeedbackService,
    authService: AuthService
  ) {
    this.isStudent = authService.isStudent();
  }

  ngOnInit(): void {
    this.loadCourses();
    this.feedbackService.catalogRatings().subscribe({
      next: (list) => this.ratings.set(new Map(list.map(r => [r.courseId, { average: r.average, count: r.count }])))
    });
    if (this.isStudent) {
      this.paymentService.getConfig().subscribe({ next: (c) => this.paymentsEnabled.set(c.enabled) });
    }
  }

  loadCourses(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.courseService.getCourses().subscribe({
      next: (data) => {
        this.courses.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de charger les cours. Vérifiez que le backend est démarré.');
      }
    });
    if (this.isStudent) {
      this.courseService.getMyEnrollments().subscribe({
        next: (enrollments) => this.enrolledIds.set(new Set(enrollments.map(e => e.courseId ?? e.course?.id!)))
      });
    }
  }

  isEnrolled(course: Course): boolean {
    return this.enrolledIds().has(course.id!);
  }

  lessonCount(course: Course): number {
    return (course.chapters || []).reduce((n, ch) => n + (ch.lessons?.length || 0), 0);
  }

  // Cours gratuit : inscription directe
  enroll(course: Course): void {
    this.busyId.set(course.id!);
    this.errorMessage.set('');
    this.courseService.enrollInCourse(course.id!).subscribe({
      next: () => {
        this.busyId.set(null);
        this.successMessage.set(`Vous êtes inscrit à « ${course.title} ».`);
        this.loadCourses();
      },
      error: (err) => {
        this.busyId.set(null);
        this.errorMessage.set(err.error?.message || "Erreur lors de l'inscription au cours.");
      }
    });
  }

  // Cours payant : le backend crée la session Stripe Checkout, puis redirection vers la page officielle Stripe
  buy(course: Course): void {
    this.busyId.set(course.id!);
    this.errorMessage.set('');
    this.paymentService.createCheckout(course.id!).subscribe({
      next: (res) => window.location.assign(res.checkoutUrl),
      error: (err) => {
        this.busyId.set(null);
        this.errorMessage.set(err.error?.message || 'Le paiement n\'a pas pu démarrer. Réessayez.');
      }
    });
  }
}
