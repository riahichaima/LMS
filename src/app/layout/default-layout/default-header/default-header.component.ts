import { CommonModule, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import {
  BadgeComponent,
  BreadcrumbRouterComponent,
  ColorModeService,
  ContainerComponent,
  DropdownComponent,
  DropdownDividerDirective,
  DropdownHeaderDirective,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  HeaderComponent,
  HeaderNavComponent,
  HeaderTogglerDirective,
  NavLinkDirective,
  SidebarToggleDirective
} from '@coreui/angular';

import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../../services/auth.service';
import { MessagingService, NotificationView } from '../../../services/messaging.service';
import { ProfileService } from '../../../services/profile.service';
import { AvatarComponent as UserAvatarComponent } from '../../../shared/avatar.component';

interface Toast {
  id: number;
  notification: NotificationView;
}

@Component({
  selector: 'app-default-header',
  templateUrl: './default-header.component.html',
  styleUrl: './default-header.component.scss',
  imports: [CommonModule, ContainerComponent, HeaderTogglerDirective, SidebarToggleDirective, IconDirective, HeaderNavComponent, NavLinkDirective, RouterLink, NgTemplateOutlet, BreadcrumbRouterComponent, DropdownComponent, DropdownToggleDirective, UserAvatarComponent, DropdownMenuDirective, DropdownHeaderDirective, DropdownItemDirective, BadgeComponent, DropdownDividerDirective]
})
export class DefaultHeaderComponent extends HeaderComponent {

  readonly #colorModeService = inject(ColorModeService);
  readonly colorMode = this.#colorModeService.colorMode;

  readonly colorModes = [
    { name: 'light', text: 'Light', icon: 'cilSun' },
    { name: 'dark', text: 'Dark', icon: 'cilMoon' },
    { name: 'auto', text: 'Auto', icon: 'cilContrast' }
  ];

  readonly icons = computed(() => {
    const currentMode = this.colorMode();
    return this.colorModes.find(mode => mode.name === currentMode)?.icon ?? 'cilSun';
  });

  readonly authService = inject(AuthService);
  readonly messaging = inject(MessagingService);
  readonly profileService = inject(ProfileService);
  readonly currentUser = this.authService.currentUserSignal;
  readonly sidebarId = input('sidebar1');

  /** La messagerie interne concerne les étudiants et les formateurs. */
  readonly canMessage = computed(() => {
    const role = this.currentUser()?.role;
    return role === 'STUDENT' || role === 'TEACHER';
  });
  readonly latestNotifications = computed(() => this.messaging.notifications().slice(0, 8));

  readonly toasts = signal<Toast[]>([]);
  private toastSeq = 0;
  private readonly router = inject(Router);

  private readonly typeIcons: Record<string, string> = {
    MESSAGE: 'cilEnvelopeClosed',
    ENROLLMENT: 'cilUserFollow',
    PAYMENT: 'cilCreditCard',
    SALE: 'cilDollar',
    WITHDRAWAL: 'cilDollar',
    ADMIN_REQUEST: 'cilUserFollow',
    RATING: 'cilStar',
    REACTION: 'cilThumbUp',
    COMMENT: 'cilCommentSquare',
    COMMENT_REPLY: 'cilCommentSquare',
    NEW_COURSE: 'cilBook',
    NEW_TEACHER: 'cilUser',
    TEACHER_RATING: 'cilStar'
  };

  constructor() {
    super();
    // Toast pour chaque notification reçue en temps réel (sauf message d'une conversation déjà ouverte)
    this.messaging.incoming$.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(n => {
      if (n.type === 'MESSAGE' && this.router.url.startsWith('/messages') && this.router.url.includes(`with=${n.relatedId}`)) {
        this.messaging.markNotificationRead(n);
        return;
      }
      const toast = { id: ++this.toastSeq, notification: n };
      this.toasts.update(list => [...list, toast].slice(-3));
      setTimeout(() => this.dismiss(toast.id), 6000);
    });
  }

  logout(): void {
    this.authService.logout();
  }

  iconFor(n: NotificationView): string {
    return this.typeIcons[n.type] ?? 'cilBell';
  }

  open(n: NotificationView, toastId?: number): void {
    this.messaging.markNotificationRead(n);
    if (toastId) this.dismiss(toastId);
    const link = this.messaging.linkFor(n);
    void this.router.navigate([link.path], { queryParams: link.query });
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  ago(iso: string): string {
    const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (minutes < 1) return "À l'instant";
    if (minutes < 60) return `Il y a ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `Il y a ${hours} h`;
    return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }
}
