import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Suppression par l'enseignant des quiz et TP générés par l'IA (refusée si des étudiants les ont rendus). */
@Injectable({ providedIn: 'root' })
export class ManualAssessmentService {
  private base = `${environment.apiUrl}/api/assessments/manual`;

  constructor(private http: HttpClient) {}

  deleteQuiz(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/quizzes/${id}`);
  }

  deleteAssignment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/assignments/${id}`);
  }
}
