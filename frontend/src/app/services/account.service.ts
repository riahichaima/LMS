import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Raison proposée avant la suppression d'un compte (liste différente pour étudiant et enseignant). */
export interface DeletionReason {
  code: string;
  label: string;
}

/** Mon compte (étudiant / enseignant) : suppression définitive avec la raison envoyée à l'administration. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private base = `${environment.apiUrl}/api/account`;

  constructor(private http: HttpClient) {}

  deletionReasons(): Observable<DeletionReason[]> {
    return this.http.get<DeletionReason[]>(`${this.base}/deletion-reasons`);
  }

  deleteMyAccount(reason: string, details: string): Observable<void> {
    return this.http.post<void>(`${this.base}/delete`, { reason, details });
  }
}
