import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  ProgressComponent,
  RowComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { CourseDetail, CourseSummary, StudentRow, TrackingService, saveBlob, scoreColor } from '../../../services/tracking.service';
import { CourseCommentView, FeedbackService, RatingSummary, Review } from '../../../services/feedback.service';
import { StarRatingComponent } from '../../../shared/star-rating.component';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
type SortKey = 'progress' | 'name' | 'quiz' | 'tp' | 'lastAccess';

/**
 * Suivi pédagogique : progression de chaque étudiant d'un cours, notes aux quiz et TP,
 * participation, statistiques par évaluation et export CSV des résultats.
 */
@Component({
  selector: 'app-teacher-tracking',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, AlertComponent, BadgeComponent, CardComponent,
    CardBodyComponent, CardHeaderComponent, ColComponent, ProgressComponent, RowComponent, SpinnerComponent, IconDirective,
    StarRatingComponent,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './teacher-tracking.component.html',
  styleUrl: './teacher-tracking.component.scss'
})
export class TeacherTrackingComponent implements OnInit {
  courses = signal<CourseSummary[]>([]);
  selectedId = signal<number | null>(null);
  detail = signal<CourseDetail | null>(null);
  loading = signal(true);
  detailLoading = signal(false);
  exporting = signal(false);
  error = signal('');
  search = signal('');
  sort = signal<SortKey>('progress');
  onlyInactive = signal(false);

  // Avis des étudiants sur le cours sélectionné
  rating = signal<RatingSummary | null>(null);
  reviews = signal<Review[]>([]);
  comments = signal<CourseCommentView[]>([]);
  replyTo = signal<number | null>(null);
  replyText = signal('');
  replying = signal(false);

  readonly scoreColor = scoreColor;

  readonly students = computed<StudentRow[]>(() => {
    const d = this.detail();
    if (!d) return [];
    const q = this.search().trim().toLowerCase();
    const rows = d.students.filter(s =>
      (!q || s.name?.toLowerCase().includes(q) || s.email?.toLowerCase().includes(q)) && (!this.onlyInactive() || !s.active));
    const by: Record<SortKey, (a: StudentRow, b: StudentRow) => number> = {
      progress: (a, b) => b.progress - a.progress,
      name: (a, b) => (a.name || '').localeCompare(b.name || ''),
      quiz: (a, b) => (b.quizAverage ?? -1) - (a.quizAverage ?? -1),
      tp: (a, b) => (b.assignmentAverage ?? -1) - (a.assignmentAverage ?? -1),
      lastAccess: (a, b) => (b.lastAccessedAt || '').localeCompare(a.lastAccessedAt || '')
    };
    return [...rows].sort(by[this.sort()]);
  });

  constructor(private trackingService: TrackingService, private feedback: FeedbackService,
              private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.trackingService.getOverview().subscribe({
      next: (o) => {
        this.courses.set(o.courses);
        this.loading.set(false);
        const fromUrl = Number(this.route.snapshot.queryParamMap.get('course'));
        const first = o.courses.find(c => c.courseId === fromUrl) ?? o.courses.find(c => c.students > 0) ?? o.courses[0];
        if (first) this.select(first.courseId);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger vos cours. Vérifiez que le backend est démarré.');
      }
    });
  }

  select(courseId: number | string): void {
    const id = Number(courseId);
    this.selectedId.set(id);
    this.detailLoading.set(true);
    this.error.set('');
    this.loadFeedback(id);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { course: id }, replaceUrl: true });
    this.trackingService.getCourse(id).subscribe({
      next: (d) => {
        if (this.selectedId() !== id) return;
        this.detail.set(d);
        this.detailLoading.set(false);
        // Arrivée depuis une notification d'avis : on descend directement à la section
        if (this.route.snapshot.queryParamMap.get('tab') === 'feedback') {
          setTimeout(() => document.getElementById('feedback')?.scrollIntoView({ behavior: 'smooth' }), 300);
        }
      },
      error: (err) => {
        this.detailLoading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger le suivi de ce cours.');
      }
    });
  }

  private loadFeedback(courseId: number): void {
    this.feedback.getRating(courseId).subscribe({ next: r => this.rating.set(r), error: () => this.rating.set(null) });
    this.feedback.reviews(courseId).subscribe({ next: r => this.reviews.set(r), error: () => this.reviews.set([]) });
    this.feedback.courseComments(courseId).subscribe({ next: c => this.comments.set(c), error: () => this.comments.set([]) });
  }

  startReply(c: CourseCommentView): void {
    this.replyTo.set(c.id);
    this.replyText.set('');
  }

  sendReply(c: CourseCommentView): void {
    const text = this.replyText().trim();
    if (!text) return;
    this.replying.set(true);
    const id = this.selectedId();
    const request = c.scope === 'COURSE' || !c.resourceId
      ? this.feedback.addComment('course', id!, text)
      : this.feedback.addComment('resource', c.resourceId, text);
    request.subscribe({
      next: () => {
        this.replying.set(false);
        this.replyTo.set(null);
        if (id) this.feedback.courseComments(id).subscribe({ next: list => this.comments.set(list) });
      },
      error: err => {
        this.replying.set(false);
        this.error.set(err.error?.message || "La réponse n'a pas pu être publiée.");
      }
    });
  }

  deleteComment(c: CourseCommentView): void {
    if (!confirm('Supprimer ce commentaire ?')) return;
    this.feedback.deleteComment(c.scope === 'COURSE' ? 'course' : 'resource', c.id).subscribe({
      next: () => this.comments.update(list => list.filter(x => x.id !== c.id)),
      error: err => this.error.set(err.error?.message || 'Suppression impossible.')
    });
  }

  exportCsv(): void {
    const d = this.detail();
    if (!d || this.exporting()) return;
    this.exporting.set(true);
    this.trackingService.exportCourse(d.courseId).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        const slug = d.title.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^\w]+/g, '_').replace(/^_|_$/g, '');
        saveBlob(blob, `resultats_${slug || 'cours'}_${new Date().toISOString().slice(0, 10)}.csv`);
      },
      error: () => {
        this.exporting.set(false);
        this.error.set("L'export des résultats a échoué.");
      }
    });
  }
}
