import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  SpinnerComponent,
  AlertComponent
  } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { CourseService, Course } from '../../services/course.service';
import { PaymentService } from '../../services/payment.service';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { StarRatingComponent } from '../../shared/star-rating.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { normalizeText } from '../../shared/ui/labels';

export type CourseSort = 'relevance' | 'price-asc' | 'price-desc' | 'rating' | 'recent';

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    StarRatingComponent,
    PageHeaderComponent,
    EmptyStateComponent,
    SpinnerComponent,
    AlertComponent,
    IconDirective
  ],
  templateUrl: './courses.component.html',
  styleUrl: './courses.component.scss'
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

  // ── Filtres du catalogue (dans le navigateur, aucune requête supplémentaire) ──
  search = signal('');
  teacher = signal('');
  priceType = signal<'' | 'FREE' | 'PAID'>('');
  /** Budget maximum en USD (null = pas de limite) */
  budget = signal<number | null>(null);
  sort = signal<CourseSort>('relevance');

  /** Enseignants présents dans le catalogue, triés par nom */
  readonly teachers = computed(() =>
    [...new Set(this.courses().map(c => c.teacherName).filter((n): n is string => !!n))]
      .sort((a, b) => a.localeCompare(b, 'fr')));

  /** Prix du cours payant le plus cher : borne du curseur de budget */
  readonly maxPrice = computed(() =>
    Math.ceil(Math.max(0, ...this.courses().filter(c => c.pricingType === 'PAID').map(c => Number(c.price) || 0))));

  readonly filteredCourses = computed(() => {
    const q = normalizeText(this.search());
    const teacher = this.teacher();
    const type = this.priceType();
    const budget = this.budget();
    const ratings = this.ratings();
    const list = this.courses().filter(c => {
      const paid = c.pricingType === 'PAID';
      if (q && !normalizeText(`${c.title} ${c.description ?? ''} ${c.category ?? ''}`).includes(q)) return false;
      if (teacher && c.teacherName !== teacher) return false;
      if (type === 'FREE' && paid) return false;
      if (type === 'PAID' && !paid) return false;
      if (budget !== null && paid && (Number(c.price) || 0) > budget) return false;
      return true;
    });
    const price = (c: Course) => (c.pricingType === 'PAID' ? Number(c.price) || 0 : 0);
    switch (this.sort()) {
      case 'price-asc': return [...list].sort((a, b) => price(a) - price(b));
      case 'price-desc': return [...list].sort((a, b) => price(b) - price(a));
      case 'rating': return [...list].sort((a, b) => (ratings.get(b.id!)?.average ?? 0) - (ratings.get(a.id!)?.average ?? 0));
      case 'recent': return [...list].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || (b.id ?? 0) - (a.id ?? 0));
      default: return list;
    }
  });

  readonly activeFilters = computed(() =>
    [this.search().trim(), this.teacher(), this.priceType(), this.budget() !== null ? 'b' : ''].filter(Boolean).length);

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

  resetFilters(): void {
    this.search.set('');
    this.teacher.set('');
    this.priceType.set('');
    this.budget.set(null);
    this.sort.set('relevance');
  }

  /** Curseur de budget : la valeur maximale signifie « pas de limite ». */
  onBudget(value: string | number): void {
    const v = Number(value);
    this.budget.set(Number.isFinite(v) && v < this.maxPrice() ? v : null);
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
