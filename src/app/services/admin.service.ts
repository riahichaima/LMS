import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { User } from './auth.service';
import { Course } from './course.service';

export interface UserWithRoleDetails {
  id: number;
  name: string;
  email: string;
  role: string;
  enabled: boolean;
  approvalStatus?: ApprovalStatus | null;
  createdAt?: string;
}

// Une demande refusée supprime le compte : il n'y a pas de statut « refusé »
export type ApprovalStatus = 'PENDING' | 'APPROVED';

// Compte administrateur vu par le super administrateur
export interface AdminAccount {
  id: number;
  name: string;
  email: string;
  enabled: boolean;
  approvalStatus: ApprovalStatus;
}

export interface AdminStatistics {
  totalUsers: number;
  totalCourses: number;
  totalEnrollments: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // Le backend renvoie le rôle sous forme d'objet { roleId, roleName } : on garde son nom
  getUsers(): Observable<UserWithRoleDetails[]> {
    return this.http.get<any[]>(`${this.apiUrl}/api/admin/users`).pipe(
      map(users => users.map(u => ({ ...u, role: typeof u.role === 'string' ? u.role : u.role?.roleName || '' })))
    );
  }

  getAdminAccounts(): Observable<AdminAccount[]> {
    return this.http.get<AdminAccount[]>(`${this.apiUrl}/api/super-admin/admins`);
  }

  approveAdmin(id: number): Observable<AdminAccount> {
    return this.http.put<AdminAccount>(`${this.apiUrl}/api/super-admin/admins/${id}/approve`, {});
  }

  // Demande refusée : le compte est supprimé
  rejectAdmin(id: number): Observable<{ id: number; deleted: boolean }> {
    return this.http.delete<{ id: number; deleted: boolean }>(`${this.apiUrl}/api/super-admin/admins/${id}`);
  }

  getUserById(id: number): Observable<UserWithRoleDetails> {
    return this.http.get<UserWithRoleDetails>(`${this.apiUrl}/api/admin/users/${id}`);
  }

  activateUser(id: number): Observable<UserWithRoleDetails> {
    return this.http.put<UserWithRoleDetails>(`${this.apiUrl}/api/admin/users/${id}/activate`, {});
  }

  deactivateUser(id: number): Observable<UserWithRoleDetails> {
    return this.http.put<UserWithRoleDetails>(`${this.apiUrl}/api/admin/users/${id}/deactivate`, {});
  }

  assignRole(id: number, roleName: string): Observable<UserWithRoleDetails> {
    return this.http.put<UserWithRoleDetails>(`${this.apiUrl}/api/admin/users/${id}/role?roleName=${roleName}`, {});
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/admin/users/${id}`);
  }

  getAdminCourses(): Observable<Course[]> {
    return this.http.get<Course[]>(`${this.apiUrl}/api/admin/courses`);
  }

  assignTeacherToCourse(courseId: number, teacherId: number): Observable<Course> {
    return this.http.put<Course>(`${this.apiUrl}/api/admin/courses/${courseId}/assign-teacher/${teacherId}`, {});
  }

  getStatistics(): Observable<AdminStatistics> {
    return this.http.get<AdminStatistics>(`${this.apiUrl}/api/admin/statistics`);
  }
}
