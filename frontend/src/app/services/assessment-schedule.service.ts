import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** QUIZ = quiz de l'IA, TP = TP de l'IA, FILE = sujet importé en fichier */
export type AssessmentKind = 'QUIZ' | 'TP' | 'FILE';

/** Règles de rendu fixées par l'enseignant (null = sans limite) */
export interface ScheduleRules {
  dueAt: string | null;
  durationMinutes: number | null;
}

/** Résumé affiché dans le cours de l'étudiant */
export interface ScheduleSummary extends ScheduleRules {
  closed: boolean;
  startedAt?: string;
  endsAt?: string;
  submitted?: boolean;
  autoSubmitted?: boolean;
  expired?: boolean;
}

/** Passage de l'étudiant : heure de fin fixée par le serveur */
export interface AttemptState extends ScheduleRules {
  kind: AssessmentKind;
  id: number;
  title: string;
  courseId: number;
  timed: boolean;
  serverNow: string;
  closed: boolean;
  started: boolean;
  startedAt?: string;
  endsAt?: string;
  remainingSeconds?: number;
  expired?: boolean;
  submitted: boolean;
  autoSubmitted?: boolean;
  draft?: string | null;
  lastResult?: { score: number; maxScore: number; feedback: string; detailedExplanation: string; submittedAt: string };
}

@Injectable({ providedIn: 'root' })
export class AssessmentScheduleService {
  private api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /** Enseignant : date limite et durée d'un quiz / TP */
  update(kind: AssessmentKind, id: number, rules: ScheduleRules): Observable<ScheduleRules> {
    return this.http.put<ScheduleRules>(`${this.api}/api/assessment-schedule/${kind}/${id}`, rules);
  }

  status(kind: AssessmentKind, id: number): Observable<AttemptState> {
    return this.http.get<AttemptState>(`${this.api}/api/student/attempts/${kind}/${id}`);
  }

  start(kind: AssessmentKind, id: number): Observable<AttemptState> {
    return this.http.post<AttemptState>(`${this.api}/api/student/attempts/${kind}/${id}/start`, {});
  }

  saveDraft(kind: AssessmentKind, id: number, draft: string): Observable<{ savedAt: string }> {
    return this.http.put<{ savedAt: string }>(`${this.api}/api/student/attempts/${kind}/${id}/draft`, { draft });
  }
}

/** « 30 min », « 1 h 30 », « 2 h » */
export function formatDuration(minutes: number | null | undefined): string {
  if (!minutes) return '';
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h} h ${String(m).padStart(2, '0')}` : `${h} h`;
}

/** « lun. 10 nov. à 23:59 » (heure locale du navigateur) */
export function formatDue(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const day = d.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });
  const time = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  return `${day} à ${time}`;
}

/** Valeur d'un champ <input type="datetime-local"> ↔ date ISO (UTC) envoyée au serveur */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(value: string | null | undefined): string | null {
  if (!value) return null;
  const d = new Date(value);
  return isNaN(d.getTime()) ? null : d.toISOString();
}
