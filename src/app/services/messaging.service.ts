import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { RealtimeService } from './realtime.service';
import { AuthService } from './auth.service';

export interface MessageView {
  id: number;
  senderId: number;
  senderName: string;
  receiverId: number;
  receiverName: string;
  content: string;
  sentAt: string;
  read: boolean;
}

export interface ContactView {
  userId: number;
  name: string;
  email: string;
  role: string;
  avatarUrl: string | null;
  courses: string[];
  lastMessage: string | null;
  lastMessageAt: string | null;
  lastFromMe: boolean;
  unreadCount: number;
}

export type NotificationType = 'MESSAGE' | 'ENROLLMENT' | 'PAYMENT' | 'SALE' | 'WITHDRAWAL' | 'ADMIN_REQUEST' | string;

export interface NotificationView {
  id: number;
  title: string;
  message: string;
  type: NotificationType;
  relatedId: number | null;
  createdAt: string;
  read: boolean;
}

/**
 * Messagerie interne (Étudiant ↔ Formateur) et notifications : API REST + événements temps réel.
 * Garde les compteurs de non-lus à jour pour l'en-tête et le menu.
 */
@Injectable({ providedIn: 'root' })
export class MessagingService {
  private apiUrl = environment.apiUrl;

  readonly unreadMessages = signal(0);
  readonly unreadNotifications = signal(0);
  readonly notifications = signal<NotificationView[]>([]);
  /** Nouvelle notification reçue en temps réel (pour les toasts). */
  readonly incoming$ = new Subject<NotificationView>();

  private wired = false;

  constructor(private http: HttpClient, private realtime: RealtimeService, private authService: AuthService) {}

  /** Démarre le temps réel et charge les compteurs (appelé par le layout après connexion). */
  start(): void {
    this.realtime.start();
    this.refreshCounts();
    this.loadNotifications();
    if (this.wired) return;
    this.wired = true;
    this.realtime.notifications$.subscribe((n: NotificationView) => {
      this.notifications.update(list => [n, ...list.filter(x => x.id !== n.id)].slice(0, 100));
      this.unreadNotifications.update(c => c + 1);
      this.incoming$.next(n);
    });
    this.realtime.messages$.subscribe(() => this.refreshCounts());
  }

  stop(): void {
    this.realtime.stop();
    this.unreadMessages.set(0);
    this.unreadNotifications.set(0);
    this.notifications.set([]);
  }

  refreshCounts(): void {
    if (!this.authService.getToken()) return;
    this.http.get<{ unreadCount: number }>(`${this.apiUrl}/api/messages/unread-count`)
      .subscribe({ next: r => this.unreadMessages.set(r.unreadCount), error: () => {} });
    this.http.get<{ unreadCount: number }>(`${this.apiUrl}/api/notifications/unread-count`)
      .subscribe({ next: r => this.unreadNotifications.set(r.unreadCount), error: () => {} });
  }

  loadNotifications(): void {
    this.http.get<NotificationView[]>(`${this.apiUrl}/api/notifications`)
      .subscribe({ next: list => this.notifications.set(list), error: () => {} });
  }

  // ── Messagerie ──
  getContacts(): Observable<ContactView[]> {
    return this.http.get<ContactView[]>(`${this.apiUrl}/api/messages/contacts`);
  }

  /** Historique ; marque les messages reçus comme lus côté serveur. */
  openConversation(userId: number): Observable<MessageView[]> {
    return this.http.get<MessageView[]>(`${this.apiUrl}/api/messages/conversation/${userId}`).pipe(
      tap(() => {
        this.refreshCounts();
        // Les notifications « nouveau message » de cet interlocuteur sont lues aussi
        this.notifications.update(list => list.map(n => (n.type === 'MESSAGE' && n.relatedId === userId ? { ...n, read: true } : n)));
      })
    );
  }

  send(receiverId: number, content: string): Observable<MessageView> {
    return this.http.post<MessageView>(`${this.apiUrl}/api/messages`, { receiverId, content });
  }

  // ── Notifications ──
  markNotificationRead(n: NotificationView): void {
    if (n.read) return;
    this.notifications.update(list => list.map(x => (x.id === n.id ? { ...x, read: true } : x)));
    this.unreadNotifications.update(c => Math.max(0, c - 1));
    this.http.put(`${this.apiUrl}/api/notifications/${n.id}/read`, {}).subscribe({ error: () => this.refreshCounts() });
  }

  markAllNotificationsRead(): void {
    this.notifications.update(list => list.map(x => ({ ...x, read: true })));
    this.unreadNotifications.set(0);
    this.http.put(`${this.apiUrl}/api/notifications/read-all`, {}).subscribe({ error: () => this.refreshCounts() });
  }

  /** Page à ouvrir quand on clique sur une notification. */
  linkFor(n: NotificationView): { path: string; query?: Record<string, any> } {
    switch (n.type) {
      case 'MESSAGE': return { path: '/messages', query: n.relatedId ? { with: n.relatedId } : undefined };
      case 'ENROLLMENT': return { path: '/teacher/courses' };
      case 'SALE': return { path: '/teacher/earnings' };
      case 'PAYMENT': return n.relatedId ? { path: `/learn/${n.relatedId}` } : { path: '/my-enrollments' };
      case 'WITHDRAWAL':
        return this.authService.isTeacher() ? { path: '/teacher/earnings' } : { path: '/admin/finance' };
      case 'ADMIN_REQUEST': return { path: '/super-admin/demandes' };
      // Avis des étudiants → section « Avis et commentaires » du suivi pédagogique
      case 'RATING':
      case 'REACTION':
      case 'COMMENT':
        return { path: '/teacher/tracking', query: n.relatedId ? { course: n.relatedId, tab: 'feedback' } : undefined };
      case 'COMMENT_REPLY': return n.relatedId ? { path: `/learn/${n.relatedId}` } : { path: '/my-enrollments' };
      case 'NEW_COURSE': return { path: '/courses' };
      case 'NEW_TEACHER': return n.relatedId ? { path: `/profile/${n.relatedId}` } : { path: '/courses' };
      case 'TEACHER_RATING': return { path: '/profile' };
      case 'MEETING':
      case 'MEETING_CANCELLED': return { path: '/meetings' };
      case 'MEETING_REMINDER': return n.relatedId ? { path: `/meetings/${n.relatedId}/room` } : { path: '/meetings' };
      case 'RECORDING': return { path: '/meetings', query: { tab: 'recordings' } };
      default: return { path: '/dashboard' };
    }
  }
}
