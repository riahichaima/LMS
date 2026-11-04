import { Component, DestroyRef, ElementRef, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../services/auth.service';
import { RealtimeService } from '../../services/realtime.service';
import { ContactView, MessageView, MessagingService } from '../../services/messaging.service';
import { AvatarComponent } from '../../shared/avatar.component';

import { PageHeaderComponent } from '../../shared/ui/page-header.component';
interface DayGroup {
  label: string;
  messages: MessageView[];
}

/**
 * Messagerie interne Étudiant ↔ Formateur : contacts (cours en commun), historique des
 * conversations et réception des messages en temps réel (WebSocket).
 */
@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IconDirective, AvatarComponent,
    PageHeaderComponent
  ],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.scss'
})
export class MessagesComponent implements OnInit {
  readonly maxLength = 2000;

  contacts = signal<ContactView[]>([]);
  contactsLoading = signal(true);
  search = signal('');
  selectedId = signal<number | null>(null);
  messages = signal<MessageView[]>([]);
  conversationLoading = signal(false);
  sending = signal(false);
  error = signal('');
  draft = signal('');

  readonly me = computed(() => this.authService.currentUserSignal()?.id ?? null);
  readonly selected = computed(() => this.contacts().find(c => c.userId === this.selectedId()) ?? null);
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    if (!q) return this.contacts();
    return this.contacts().filter(c =>
      c.name?.toLowerCase().includes(q) || c.courses.some(t => t.toLowerCase().includes(q)));
  });
  /** Messages regroupés par jour pour les séparateurs de date. */
  readonly days = computed<DayGroup[]>(() => {
    const groups: DayGroup[] = [];
    for (const m of this.messages()) {
      const label = this.dayLabel(m.sentAt);
      const last = groups[groups.length - 1];
      if (last && last.label === label) last.messages.push(m);
      else groups.push({ label, messages: [m] });
    }
    return groups;
  });
  /** Dernier message envoyé par moi : affiche « Lu » / « Envoyé ». */
  readonly lastMineId = computed(() => {
    const mine = this.messages().filter(m => m.senderId === this.me());
    return mine.length ? mine[mine.length - 1].id : null;
  });

  @ViewChild('scrollArea') private scrollArea?: ElementRef<HTMLDivElement>;

  private destroyRef = inject(DestroyRef);

  constructor(
    public authService: AuthService,
    public realtime: RealtimeService,
    private messaging: MessagingService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadContacts(() => {
      const withId = Number(this.route.snapshot.queryParamMap.get('with'));
      if (withId) this.select(withId);
    });

    // Lien de notification « nouveau message » alors que la page est déjà ouverte
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const withId = Number(params.get('with'));
      if (withId && withId !== this.selectedId() && !this.contactsLoading()) this.select(withId);
    });

    this.realtime.messages$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((m: MessageView) => this.onIncoming(m));
    this.realtime.reads$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(r => {
      if (r.readerId === this.selectedId()) {
        this.messages.update(list => list.map(m => (m.senderId === this.me() ? { ...m, read: true } : m)));
      }
    });
  }

  select(userId: number): void {
    if (!this.contacts().some(c => c.userId === userId)) {
      this.error.set("Ce contact n'est pas disponible : vous pouvez écrire aux formateurs de vos cours ou aux étudiants de vos cours.");
      return;
    }
    this.selectedId.set(userId);
    this.error.set('');
    this.draft.set('');
    this.messages.set([]);
    this.conversationLoading.set(true);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { with: userId }, replaceUrl: true });
    this.messaging.openConversation(userId).subscribe({
      next: list => {
        if (this.selectedId() !== userId) return;
        this.messages.set(list);
        this.conversationLoading.set(false);
        this.patchContact(userId, { unreadCount: 0 });
        this.scrollToBottom();
      },
      error: err => {
        this.conversationLoading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger la conversation.');
      }
    });
  }

  back(): void {
    this.selectedId.set(null);
    void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  send(): void {
    const to = this.selectedId();
    const content = this.draft().trim();
    if (!to || !content || this.sending() || content.length > this.maxLength) return;
    this.sending.set(true);
    this.error.set('');
    this.messaging.send(to, content).subscribe({
      next: m => {
        this.sending.set(false);
        this.draft.set('');
        this.append(m);
      },
      error: err => {
        this.sending.set(false);
        this.error.set(err.error?.message || "Le message n'a pas pu être envoyé.");
      }
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  roleLabel(role: string): string {
    return role === 'TEACHER' ? 'Formateur' : role === 'STUDENT' ? 'Étudiant' : role;
  }

  /** Heure si aujourd'hui, sinon date courte (liste des contacts). */
  shortDate(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    return d.toDateString() === now.toDateString()
      ? d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
      : d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }

  time(iso: string): string {
    return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  private onIncoming(m: MessageView): void {
    const me = this.me();
    const other = m.senderId === me ? m.receiverId : m.senderId;
    if (!this.contacts().some(c => c.userId === other)) {
      this.loadContacts();
      return;
    }
    if (other === this.selectedId()) {
      this.append(m);
      // Message reçu dans la conversation ouverte : marqué lu côté serveur
      if (m.senderId !== me) {
        this.messaging.openConversation(other).subscribe({
          next: list => { if (this.selectedId() === other) this.messages.set(list); }
        });
      }
    } else {
      this.touchContact(other, m, m.senderId !== me);
    }
  }

  private append(m: MessageView): void {
    if (this.messages().some(x => x.id === m.id)) return;
    this.messages.update(list => [...list, m]);
    const other = m.senderId === this.me() ? m.receiverId : m.senderId;
    this.touchContact(other, m, false);
    this.scrollToBottom();
  }

  /** Met à jour l'aperçu du contact et le remonte en tête de liste. */
  private touchContact(userId: number, m: MessageView, unread: boolean): void {
    this.contacts.update(list => {
      const c = list.find(x => x.userId === userId);
      if (!c) return list;
      const updated: ContactView = {
        ...c,
        lastMessage: m.content,
        lastMessageAt: m.sentAt,
        lastFromMe: m.senderId === this.me(),
        unreadCount: unread ? c.unreadCount + 1 : c.unreadCount
      };
      return [updated, ...list.filter(x => x.userId !== userId)];
    });
  }

  private patchContact(userId: number, patch: Partial<ContactView>): void {
    this.contacts.update(list => list.map(c => (c.userId === userId ? { ...c, ...patch } : c)));
  }

  private loadContacts(done?: () => void): void {
    this.messaging.getContacts().subscribe({
      next: list => {
        this.contacts.set(list);
        this.contactsLoading.set(false);
        done?.();
      },
      error: () => {
        this.contactsLoading.set(false);
        this.error.set('Impossible de charger vos contacts. Vérifiez que le backend est démarré.');
      }
    });
  }

  private dayLabel(iso: string): string {
    const d = new Date(iso);
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    if (d.toDateString() === today.toDateString()) return "Aujourd'hui";
    if (d.toDateString() === yesterday.toDateString()) return 'Hier';
    return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.scrollArea?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
