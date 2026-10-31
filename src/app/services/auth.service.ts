import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

export interface User {
  id: number;
  name: string;
  email: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface MessageResponse {
  message: string;
}

// Même règle que le backend (RegisterRequest / PasswordResetRequest)
export const PASSWORD_PATTERN = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!._-]).{8,}$/;
export const PASSWORD_RULE_MESSAGE =
  'Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial (@#$%^&+=!._-).';

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  role?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = environment.apiUrl;

  public currentUserSignal = signal<User | null>(this.getStoredUser());
  public isLoggedIn = computed(() => !!this.currentUserSignal());
  public userRole = computed(() => this.currentUserSignal()?.role || '');

  constructor(private http: HttpClient, private router: Router) {
    if (this.getToken() && !this.currentUserSignal()) {
      // Token invalide au démarrage : on vide la session sans quitter la page (l'accueil reste affiché)
      this.fetchCurrentUser().subscribe({
        error: () => this.clearSession()
      });
    }
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap(response => {
        if (response.token) {
          this.setToken(response.token);
          this.setUser(response.user);
        }
      })
    );
  }

  register(data: RegisterRequest): Observable<User> {
    const endpoint = data.role === 'ADMIN' ? '/admin/register' :
                     data.role === 'TEACHER' ? '/teacher/register' :
                     data.role === 'STUDENT' ? '/student/register' : '/auth/register';
    return this.http.post<User>(`${this.apiUrl}${endpoint}`, data);
  }

  verifyEmail(token: string): Observable<MessageResponse> {
    return this.http.get<MessageResponse>(`${this.apiUrl}/auth/verify`, { params: { token } });
  }

  resendVerification(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/resend-verification`, { email });
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/reset-password`, { token, newPassword });
  }

  // Page d'accueil selon le rôle de l'utilisateur connecté
  getHomeRoute(): string {
    if (this.isAdmin()) return '/admin/users';
    if (this.isTeacher()) return '/teacher/courses';
    return '/courses';
  }

  isAdmin(): boolean {
    const role = this.userRole();
    return role === 'ADMIN' || role === 'ROLE_ADMIN';
  }

  isTeacher(): boolean {
    const role = this.userRole();
    return role === 'TEACHER' || role === 'ROLE_TEACHER';
  }

  isStudent(): boolean {
    const role = this.userRole();
    return role === 'STUDENT' || role === 'ROLE_STUDENT';
  }

  fetchCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/auth/me`).pipe(
      tap(user => this.setUser(user)),
      catchError(err => {
        this.clearSession();
        return throwError(() => err);
      })
    );
  }

  // Déconnexion : retour à la page d'accueil
  logout(): void {
    this.clearSession();
    this.router.navigate(['/']);
  }

  clearSession(): void {
    localStorage.removeItem('lms_token');
    localStorage.removeItem('lms_user');
    this.currentUserSignal.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem('lms_token');
  }

  private setToken(token: string): void {
    localStorage.setItem('lms_token', token);
  }

  private setUser(user: User): void {
    localStorage.setItem('lms_user', JSON.stringify(user));
    this.currentUserSignal.set(user);
  }

  private getStoredUser(): User | null {
    const userJson = localStorage.getItem('lms_user');
    if (userJson) {
      try {
        return JSON.parse(userJson);
      } catch (e) {
        return null;
      }
    }
    return null;
  }
}
