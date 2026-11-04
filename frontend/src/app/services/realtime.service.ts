import { Injectable, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Connexion WebSocket STOMP au backend (/ws/websocket), authentifiée par le JWT.
 * Le serveur pousse dans les files privées de l'utilisateur :
 *   /user/queue/messages, /user/queue/notifications, /user/queue/read, /user/queue/meeting
 * Le client n'envoie que la signalisation des réunions (/app/meeting.*).
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  readonly connected = signal(false);
  readonly messages$ = new Subject<any>();
  readonly notifications$ = new Subject<any>();
  readonly reads$ = new Subject<{ readerId: number; conversationWith: number }>();
  /** Événements des salles de réunion (welcome, signal, chat, state, peer-joined, peer-left, error). */
  readonly meeting$ = new Subject<any>();
  /** Émis à chaque (re)connexion : une salle ouverte s'y réinscrit. */
  readonly connects$ = new Subject<void>();

  private client: Client | null = null;

  constructor(private authService: AuthService) {}

  start(): void {
    if (this.client?.active || !this.authService.getToken()) return;
    const client = new Client({
      brokerURL: environment.apiUrl.replace(/^http/, 'ws') + '/ws/websocket',
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      // Jeton relu à chaque (re)connexion
      beforeConnect: () => {
        const token = this.authService.getToken();
        if (!token) {
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        this.connected.set(true);
        client.subscribe('/user/queue/messages', m => this.messages$.next(this.parse(m)));
        client.subscribe('/user/queue/notifications', m => this.notifications$.next(this.parse(m)));
        client.subscribe('/user/queue/read', m => this.reads$.next(this.parse(m)));
        client.subscribe('/user/queue/meeting', m => this.meeting$.next(this.parse(m)));
        this.connects$.next();
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false)
    });
    this.client = client;
    client.activate();
  }

  stop(): void {
    const client = this.client;
    this.client = null;
    this.connected.set(false);
    if (client) void client.deactivate();
  }

  /** Envoi d'un message de signalisation de réunion (ignoré si la connexion est coupée). */
  publish(destination: string, body: unknown): boolean {
    if (!this.client?.connected) return false;
    this.client.publish({ destination, body: JSON.stringify(body) });
    return true;
  }

  private parse(m: IMessage): any {
    try {
      return JSON.parse(m.body);
    } catch {
      return m.body;
    }
  }
}
