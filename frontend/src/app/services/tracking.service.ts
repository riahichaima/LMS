import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Suivi pédagogique du formateur : progression, notes, participation, export. */

export interface TrackingKpis {
  courses: number;
  students: number;
  enrollments: number;
  averageProgress: number;
  completionRate: number;
  completed: number;
  quizAverage: number | null;
  assignmentAverage: number | null;
  submissions: number;
  participationRate: number;
  activeLast7Days: number;
  passRate: number | null;
}

export interface CourseSummary {
  courseId: number;
  title: string;
  pricingType: string;
  students: number;
  averageProgress: number;
  completed: number;
  quizAverage: number | null;
  assignmentAverage: number | null;
  submissions: number;
  participationRate: number;
  assessments: number;
}

export interface AssessmentStats {
  id: number;
  type: 'QUIZ' | 'TP';
  title: string;
  courseId: number;
  courseTitle: string;
  attempts: number;
  students: number;
  averageScore: number | null;
  bestScore: number | null;
  passRate: number | null;
  passingScore: number | null;
}

export interface RecentSubmission {
  type: 'QUIZ' | 'TP';
  title: string;
  courseTitle: string;
  studentName: string;
  percent: number | null;
  submittedAt: string;
}

export interface TrackingOverview {
  kpis: TrackingKpis;
  courses: CourseSummary[];
  assessments: AssessmentStats[];
  recentSubmissions: RecentSubmission[];
}

export interface StudentRow {
  studentId: number;
  name: string;
  email: string;
  enrolledAt: string | null;
  lastAccessedAt: string | null;
  progress: number;
  lessonsCompleted: number;
  totalLessons: number;
  completed: boolean;
  quizzesTaken: number;
  quizAverage: number | null;
  assignmentsSubmitted: number;
  assignmentAverage: number | null;
  active: boolean;
}

export interface CourseDetail {
  courseId: number;
  title: string;
  totalLessons: number;
  kpis: TrackingKpis;
  students: StudentRow[];
  assessments: AssessmentStats[];
}

@Injectable({ providedIn: 'root' })
export class TrackingService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getOverview(): Observable<TrackingOverview> {
    return this.http.get<TrackingOverview>(`${this.apiUrl}/api/teacher/tracking`);
  }

  getCourse(courseId: number): Observable<CourseDetail> {
    return this.http.get<CourseDetail>(`${this.apiUrl}/api/teacher/tracking/courses/${courseId}`);
  }

  exportCourse(courseId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/api/teacher/tracking/courses/${courseId}/export`, { responseType: 'blob' });
  }
}

/** Couleur de badge pour une note en %. */
export function scoreColor(value: number | null | undefined): string {
  if (value === null || value === undefined) return 'secondary';
  return value >= 75 ? 'success' : value >= 50 ? 'warning' : 'danger';
}

/** Téléchargement d'un fichier reçu en Blob (export CSV). */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
