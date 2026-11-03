import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ScheduleSummary } from './assessment-schedule.service';

/** Quiz et TP importés en fichier par l'enseignant ; copies des étudiants et notes sur 20. */

export interface FileSubmission {
  id: number;
  studentId: number;
  studentName: string;
  studentEmail: string;
  originalName: string;
  sizeBytes: number;
  submittedAt: string;
  grade: number | null;
  feedback: string | null;
  gradedAt: string | null;
}

export interface AssessmentFile {
  id: number;
  type: 'QUIZ' | 'TP';
  title: string;
  instructions: string | null;
  difficultyLevel: string;
  courseId: number;
  courseTitle: string;
  chapterId: number;
  chapterTitle: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: string;
  submissionCount: number;
  gradedCount: number;
  mySubmission: FileSubmission | null;
  /** Date limite de rendu et temps de travail (null = sans limite) */
  dueAt: string | null;
  durationMinutes: number | null;
  /** Étudiant : état de son passage */
  schedule?: ScheduleSummary | null;
}

/** Formats acceptés (contenu vérifié côté serveur) */
export const ACCEPTED_FILES = '.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.odt,.txt,.md,.zip,.png,.jpg,.jpeg,.java,.py,.js,.ts,.c,.cpp,.sql,.html,.css,.json';

export function fileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} Ko`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} Mo`;
}

@Injectable({ providedIn: 'root' })
export class AssessmentFileService {
  private base = `${environment.apiUrl}/api/assessment-files`;

  constructor(private http: HttpClient) {}

  upload(data: { chapterId: number; type: string; title: string; instructions: string; difficultyLevel: string;
                  dueAt?: string | null; durationMinutes?: number | null }, file: File): Observable<HttpEvent<AssessmentFile>> {
    const form = new FormData();
    form.append('chapterId', String(data.chapterId));
    form.append('type', data.type);
    form.append('title', data.title);
    form.append('instructions', data.instructions);
    form.append('difficultyLevel', data.difficultyLevel);
    if (data.dueAt) form.append('dueAt', data.dueAt);
    if (data.durationMinutes) form.append('durationMinutes', String(data.durationMinutes));
    form.append('file', file, file.name);
    return this.http.post<AssessmentFile>(this.base, form, { reportProgress: true, observe: 'events' });
  }

  teacherFiles(): Observable<AssessmentFile[]> {
    return this.http.get<AssessmentFile[]>(`${this.base}/teacher`);
  }

  submissions(fileId: number): Observable<FileSubmission[]> {
    return this.http.get<FileSubmission[]>(`${this.base}/${fileId}/submissions`);
  }

  grade(submissionId: number, grade: number, feedback: string): Observable<FileSubmission> {
    return this.http.put<FileSubmission>(`${this.base}/submissions/${submissionId}/grade`, { grade, feedback });
  }

  delete(fileId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${fileId}`);
  }

  submit(fileId: number, file: File): Observable<HttpEvent<FileSubmission>> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<FileSubmission>(`${this.base}/${fileId}/submission`, form, { reportProgress: true, observe: 'events' });
  }

  downloadSubject(fileId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${fileId}/download`, { responseType: 'blob' });
  }

  downloadSubmission(submissionId: number): Observable<Blob> {
    return this.http.get(`${this.base}/submissions/${submissionId}/download`, { responseType: 'blob' });
  }

  /** Enregistre un Blob reçu sous son nom d'origine. */
  static save(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
