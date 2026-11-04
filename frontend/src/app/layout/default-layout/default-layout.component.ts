import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { NgScrollbar } from 'ngx-scrollbar';
import { INavData } from '@coreui/angular';
import {
  ContainerComponent,
  ShadowOnScrollDirective,
  SidebarBrandComponent,
  SidebarComponent,
  SidebarFooterComponent,
  SidebarHeaderComponent,
  SidebarNavComponent,
  SidebarToggleDirective,
  SidebarTogglerDirective
} from '@coreui/angular';

import { DefaultFooterComponent, DefaultHeaderComponent } from './';
import { adminNavItems, superAdminNavItems, teacherNavItems, studentNavItems } from './_nav';
import { AuthService } from '../../services/auth.service';
import { CourseChatbotComponent } from '../../views/learn/course-chatbot.component';
import { MessagingService } from '../../services/messaging.service';
import { ProfileService } from '../../services/profile.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './default-layout.component.html',
  styleUrls: ['./default-layout.component.scss'],
  // Thème bleu LMS des espaces connectés (scss/_lms-app.scss)
  host: { class: 'lms-app' },
  imports: [
    SidebarComponent,
    SidebarHeaderComponent,
    SidebarBrandComponent,
    SidebarNavComponent,
    SidebarFooterComponent,
    SidebarToggleDirective,
    SidebarTogglerDirective,
    ContainerComponent,
    DefaultFooterComponent,
    DefaultHeaderComponent,
    NgScrollbar,
    RouterOutlet,
    RouterLink,
    ShadowOnScrollDirective,
    CourseChatbotComponent
  ]
})
export class DefaultLayoutComponent implements OnInit, OnDestroy {
  private baseNavItems = signal<INavData[]>([]);
  // Compteur de messages non lus sur l'entrée « Messagerie » du menu (comme Messenger)
  public navItems = computed<INavData[]>(() => {
    const unread = this.messaging.unreadMessages();
    return this.baseNavItems().map(item =>
      item.url === '/messages' && unread > 0
        ? { ...item, badge: { color: 'danger', text: unread > 99 ? '99+' : String(unread) } }
        : item
    );
  });

  private router = inject(Router);
  private messaging = inject(MessagingService);
  private profileService = inject(ProfileService);
  // Adresse courante : le lecteur de cours (/learn/...) a son propre tuteur IA ; masqué sur la messagerie
  private url = toSignal(
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url }
  );

  constructor(public authService: AuthService) {}

  showGlobalChatbot(): boolean {
    return this.authService.isStudent() && !this.url().startsWith('/learn/') && !this.url().startsWith('/messages');
  }

  ngOnInit(): void {
    // Messagerie et notifications en temps réel (WebSocket)
    this.messaging.start();
    this.profileService.loadMe();
    if (this.authService.isSuperAdmin()) {
      this.baseNavItems.set(superAdminNavItems);
    } else if (this.authService.isAdmin()) {
      this.baseNavItems.set(adminNavItems);
    } else if (this.authService.isTeacher()) {
      this.baseNavItems.set(teacherNavItems);
    } else {
      this.baseNavItems.set(studentNavItems);
    }
  }

  ngOnDestroy(): void {
    this.messaging.stop();
    this.profileService.clear();
  }
}
