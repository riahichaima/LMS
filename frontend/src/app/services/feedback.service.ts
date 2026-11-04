import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Avis des étudiants : note des cours (étoiles), évaluation des formateurs (%),
 * j'aime / je n'aime pas et commentaires sur les cours et leurs ressources, profil formateur.
 */

export interface MyRating {
  stars: number;
  comment: string | null;
  updatedAt: string;
}

export interface RatingSummary {
  courseId: number;
  average: number | null;
  count: number;
  /** [nb 1★, nb 2★, nb 3★, nb 4★, nb 5★] */
  distribution: number[];
  satisfactionPercent: number | null;
  myRating: MyRating | null;
  canRate: boolean;
}

export interface CourseRatingLine {
  courseId: number;
  average: number;
  count: number;
}

export interface Review {
  id: number;
  studentName: string;
  courseId: number;
  courseTitle: string;
  stars: number;
  comment: string | null;
  date: string;
}

export interface MyTeacherRating {
  percent: number;
  comment: string | null;
  updatedAt: string;
}

export interface TeacherRatingSummary {
  teacherId: number;
  average: number | null;
  count: number;
  /** [0-20 %, 21-40 %, 41-60 %, 61-80 %, 81-100 %] */
  buckets: number[];
  myRating: MyTeacherRating | null;
  canRate: boolean;
}

export interface Evaluation {
  id: number;
  studentName: string;
  percent: number;
  comment: string | null;
  date: string;
}

/** J'aime / je n'aime pas + nombre de commentaires d'un cours entier ou d'une ressource. */
export interface ReactionSummary {
  targetId: number;
  likes: number;
  dislikes: number;
  myReaction: 'LIKE' | 'DISLIKE' | null;
  commentCount: number;
  canReact: boolean;
  canComment: boolean;
}

export interface CommentView {
  id: number;
  authorId: number;
  authorName: string;
  authorRole: string;
  authorAvatarUrl: string | null;
  fromTeacher: boolean;
  content: string;
  createdAt: string;
  mine: boolean;
  canDelete: boolean;
}

/** Commentaire vu par le formateur : sur le cours entier (COURSE) ou sous un contenu (RESOURCE). */
export interface CourseCommentView {
  id: number;
  scope: 'COURSE' | 'RESOURCE';
  resourceId: number | null;
  resourceTitle: string;
  resourceType: string | null;
  lessonTitle: string | null;
  authorId: number;
  authorName: string;
  fromTeacher: boolean;
  content: string;
  createdAt: string;
}

export interface ProfileCourse {
  courseId: number;
  title: string;
  pricingType: string;
  students: number;
  ratingAverage: number | null;
  ratingCount: number;
}

export interface TeacherProfile {
  teacherId: number;
  name: string;
  memberSince: string | null;
  courses: number;
  students: number;
  enrollments: number;
  evaluationAverage: number | null;
  evaluationCount: number;
  evaluationBuckets: number[];
  ratingAverage: number | null;
  ratingCount: number;
  satisfactionPercent: number | null;
  distribution: number[];
  likes: number;
  dislikes: number;
  positivePercent: number | null;
  courseList: ProfileCourse[];
  recentReviews: Review[];
  recentEvaluations: Evaluation[];
}

export type FeedbackKind = 'course' | 'resource';

@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private base = `${environment.apiUrl}/api/feedback`;

  constructor(private http: HttpClient) {}

  // ── Cours : étoiles ──
  catalogRatings(): Observable<CourseRatingLine[]> {
    return this.http.get<CourseRatingLine[]>(`${this.base}/ratings`);
  }

  getRating(courseId: number): Observable<RatingSummary> {
    return this.http.get<RatingSummary>(`${this.base}/courses/${courseId}/rating`);
  }

  rate(courseId: number, stars: number, comment: string | null): Observable<RatingSummary> {
    return this.http.put<RatingSummary>(`${this.base}/courses/${courseId}/rating`, { stars, comment });
  }

  deleteRating(courseId: number): Observable<RatingSummary> {
    return this.http.delete<RatingSummary>(`${this.base}/courses/${courseId}/rating`);
  }

  reviews(courseId: number): Observable<Review[]> {
    return this.http.get<Review[]>(`${this.base}/courses/${courseId}/reviews`);
  }

  /** Tous les commentaires d'un cours (vue formateur). */
  courseComments(courseId: number): Observable<CourseCommentView[]> {
    return this.http.get<CourseCommentView[]>(`${this.base}/courses/${courseId}/comments`);
  }

  // ── J'aime / commentaires : cours entier ou ressource ──
  summary(kind: FeedbackKind, id: number): Observable<ReactionSummary> {
    return this.http.get<ReactionSummary>(kind === 'course' ? `${this.base}/courses/${id}/feedback` : `${this.base}/resources/${id}`);
  }

  react(kind: FeedbackKind, id: number, type: 'LIKE' | 'DISLIKE'): Observable<ReactionSummary> {
    return this.http.post<ReactionSummary>(`${this.base}/${kind === 'course' ? 'courses' : 'resources'}/${id}/reaction`, { type });
  }

  comments(kind: FeedbackKind, id: number): Observable<CommentView[]> {
    return this.http.get<CommentView[]>(kind === 'course' ? `${this.base}/courses/${id}/discussion` : `${this.base}/resources/${id}/comments`);
  }

  addComment(kind: FeedbackKind, id: number, content: string): Observable<CommentView> {
    return this.http.post<CommentView>(
      kind === 'course' ? `${this.base}/courses/${id}/discussion` : `${this.base}/resources/${id}/comments`, { content });
  }

  deleteComment(kind: FeedbackKind, commentId: number): Observable<void> {
    return this.http.delete<void>(kind === 'course' ? `${this.base}/course-comments/${commentId}` : `${this.base}/comments/${commentId}`);
  }

  // ── Formateurs : évaluation en % ──
  teacherRating(teacherId: number): Observable<TeacherRatingSummary> {
    return this.http.get<TeacherRatingSummary>(`${this.base}/teachers/${teacherId}/rating`);
  }

  rateTeacher(teacherId: number, percent: number, comment: string | null): Observable<TeacherRatingSummary> {
    return this.http.put<TeacherRatingSummary>(`${this.base}/teachers/${teacherId}/rating`, { percent, comment });
  }

  deleteTeacherRating(teacherId: number): Observable<TeacherRatingSummary> {
    return this.http.delete<TeacherRatingSummary>(`${this.base}/teachers/${teacherId}/rating`);
  }
}
