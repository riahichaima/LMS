import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Supervision (admin / super admin) : statistiques globales et journal système. */

export interface GlobalStats {
  generatedAt: string;
  users: {
    total: number;
    byRole: Record<string, number>;
    enabled: number;
    disabled: number;
    pendingAdmins: number;
    newLast30Days: number;
  };
  courses: { total: number; free: number; paid: number; withoutStudents: number; teachers: number };
  learning: {
    enrollments: number;
    completed: number;
    completionRate: number;
    averageProgress: number;
    lessonsCompleted: number;
    quizSubmissions: number;
    assignmentSubmissions: number;
    quizAverage: number | null;
    assignmentAverage: number | null;
    activeStudents7Days: number;
  };
  finance: { paidPayments: number; grossRevenue: number; platformCommission: number; currency: string };
  communication: { messages: number; messagesLast7Days: number };
  system: { logsLast24h: number; errorsLast24h: number; securityLast24h: number; warningsLast24h: number };
  series: {
    days: string[];
    registrations: number[];
    enrollments: number[];
    submissions: number[];
    logins: number[];
    payments: number[];
  };
  topCourses: { courseId: number; title: string; teacher: string | null; pricingType: string; students: number; averageProgress: number }[];
}

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'SECURITY';

export interface SystemLog {
  id: number;
  createdAt: string;
  level: LogLevel;
  category: string;
  action: string;
  message: string | null;
  actorEmail: string | null;
  actorRole: string | null;
  ipAddress: string | null;
  httpMethod: string | null;
  path: string | null;
  status: number | null;
  durationMs: number | null;
}

export interface LogPage {
  content: SystemLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LogFilters {
  level?: string;
  category?: string;
  q?: string;
  from?: string;
  to?: string;
}

@Injectable({ providedIn: 'root' })
export class SupervisionService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getStats(): Observable<GlobalStats> {
    return this.http.get<GlobalStats>(`${this.apiUrl}/api/supervision/stats`);
  }

  getLogs(filters: LogFilters, page: number, size: number): Observable<LogPage> {
    return this.http.get<LogPage>(`${this.apiUrl}/api/supervision/logs`, {
      params: this.params(filters).set('page', page).set('size', size)
    });
  }

  exportLogs(filters: LogFilters): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/api/supervision/logs/export`, { params: this.params(filters), responseType: 'blob' });
  }

  private params(filters: LogFilters): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value) params = params.set(key, value);
    }
    return params;
  }
}
