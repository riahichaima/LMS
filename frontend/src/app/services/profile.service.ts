import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { TeacherProfile } from './feedback.service';

/** Profils : identité (prénom, nom, photo, rôle) + cours suivis (étudiant) ou statistiques (formateur). */

export interface StudentCourse {
  courseId: number;
  title: string;
  teacherId: number | null;
  teacherName: string | null;
  progress: number;
  completed: boolean;
  enrolledAt: string | null;
  lastAccessedAt: string | null;
}

export interface FollowedTeacher {
  teacherId: number;
  name: string;
  avatarUrl: string | null;
  courses: string[];
}

export interface StudentPart {
  coursesCount: number;
  completedCount: number;
  averageProgress: number;
  courses: StudentCourse[];
  teachers: FollowedTeacher[];
  /** Vue d'un formateur : seuls ses cours avec cet étudiant sont listés. */
  limited: boolean;
}

export interface ProfileView {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  name: string;
  role: 'STUDENT' | 'TEACHER' | 'ADMIN' | 'SUPER_ADMIN' | string;
  email: string | null;
  avatarUrl: string | null;
  memberSince: string | null;
  self: boolean;
  student: StudentPart | null;
  teacher: TeacherProfile | null;
}

export const ROLE_LABELS: Record<string, string> = {
  STUDENT: 'Étudiant',
  TEACHER: 'Formateur',
  ADMIN: 'Administrateur',
  SUPER_ADMIN: 'Super administrateur'
};

/** URL complète d'une photo de profil (chemin relatif renvoyé par l'API). */
export function avatarSrc(url: string | null | undefined): string | null {
  return url ? environment.apiUrl + url : null;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private base = `${environment.apiUrl}/api/profiles`;

  /** Mon profil (photo affichée dans l'en-tête). */
  readonly me = signal<ProfileView | null>(null);

  constructor(private http: HttpClient, private authService: AuthService) {}

  loadMe(): void {
    if (!this.authService.getToken()) return;
    this.http.get<ProfileView>(`${this.base}/me`).subscribe({ next: p => this.me.set(p), error: () => {} });
  }

  clear(): void {
    this.me.set(null);
  }

  getMe(): Observable<ProfileView> {
    return this.http.get<ProfileView>(`${this.base}/me`).pipe(tap(p => this.me.set(p)));
  }

  get(userId: number): Observable<ProfileView> {
    return this.http.get<ProfileView>(`${this.base}/${userId}`);
  }

  updateNames(firstName: string, lastName: string): Observable<ProfileView> {
    return this.http.put<ProfileView>(`${this.base}/me`, { firstName, lastName }).pipe(tap(p => {
      this.me.set(p);
      this.authService.updateCurrentUserName(p.name);
    }));
  }

  uploadAvatar(file: File): Observable<ProfileView> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ProfileView>(`${this.base}/me/avatar`, form).pipe(tap(p => this.me.set(p)));
  }

  deleteAvatar(): Observable<ProfileView> {
    return this.http.delete<ProfileView>(`${this.base}/me/avatar`).pipe(tap(p => this.me.set(p)));
  }
}
