import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { User } from './auth.service';
import { Course } from './course.service';

export interface UserWithRoleDetails {
  id: number;
  name: string;
  email: string;
  role: string;
  enabled: boolean;
  createdAt?: string;
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

  getUsers(): Observable<UserWithRoleDetails[]> {
    return this.http.get<UserWithRoleDetails[]>(`${this.apiUrl}/api/admin/users`);
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
