import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Réclamations : formulaire des étudiants et enseignants, traitement par l'administration. */

export type ComplaintStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED';

export interface ComplaintView {
  id: number;
  category: string;
  subject: string;
  message: string;
  status: ComplaintStatus;
  adminResponse: string | null;
  authorId: number;
  authorName: string;
  authorEmail: string;
  authorRole: string;
  handledByName: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface ComplaintStats {
  open: number;
  inProgress: number;
  resolved: number;
  rejected: number;
}

export const COMPLAINT_CATEGORIES: { value: string; label: string; icon: string }[] = [
  { value: 'TECHNIQUE', label: 'Problème technique', icon: 'cilSettings' },
  { value: 'COURS', label: 'Contenu d\'un cours', icon: 'cilBook' },
  { value: 'PAIEMENT', label: 'Paiement', icon: 'cilCreditCard' },
  { value: 'COMPTE', label: 'Mon compte', icon: 'cilUser' },
  { value: 'ENSEIGNANT', label: 'Enseignant / étudiant', icon: 'cilPeople' },
  { value: 'AUTRE', label: 'Autre', icon: 'cilCommentSquare' }
];

export const COMPLAINT_STATUS: Record<ComplaintStatus, { label: string; tone: string }> = {
  OPEN: { label: 'Envoyée', tone: 'info' },
  IN_PROGRESS: { label: 'En cours', tone: 'warning' },
  RESOLVED: { label: 'Résolue', tone: 'success' },
  REJECTED: { label: 'Rejetée', tone: 'neutral' }
};

export function categoryLabel(value: string): string {
  return COMPLAINT_CATEGORIES.find(c => c.value === value)?.label ?? value;
}

@Injectable({ providedIn: 'root' })
export class ComplaintService {
  private base = `${environment.apiUrl}/api/complaints`;

  constructor(private http: HttpClient) {}

  create(category: string, subject: string, message: string): Observable<ComplaintView> {
    return this.http.post<ComplaintView>(this.base, { category, subject, message });
  }

  mine(): Observable<ComplaintView[]> {
    return this.http.get<ComplaintView[]>(`${this.base}/mine`);
  }

  all(): Observable<ComplaintView[]> {
    return this.http.get<ComplaintView[]>(this.base);
  }

  handle(id: number, status: ComplaintStatus, response: string): Observable<ComplaintView> {
    return this.http.put<ComplaintView>(`${this.base}/${id}`, { status, response });
  }
}
