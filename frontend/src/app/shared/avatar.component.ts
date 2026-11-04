import { Component, computed, effect, input, signal } from '@angular/core';
import { avatarSrc } from '../services/profile.service';

/** Photo de profil ronde ; initiales si pas de photo (ou si l'image ne charge pas). */
@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `
    @if (src() && !broken()) {
      <img class="av" [src]="src()" [alt]="'Photo de ' + (name() || 'l\\'utilisateur')"
           [style.width.px]="size()" [style.height.px]="size()" (error)="broken.set(true)">
    } @else {
      <span class="av initials" [style.width.px]="size()" [style.height.px]="size()" [style.font-size.px]="size() * 0.38"
            [attr.aria-label]="name() || 'Utilisateur'" role="img">{{ initials() }}</span>
    }
  `,
  styles: [`
    :host { display: inline-flex; flex-shrink: 0; }
    .av { border-radius: 50%; object-fit: cover; display: inline-grid; place-items: center; }
    .initials { background: rgba(var(--cui-primary-rgb), .15); color: var(--cui-primary); font-weight: 700; line-height: 1; }
  `]
})
export class AvatarComponent {
  name = input<string | null | undefined>('');
  /** Chemin relatif renvoyé par l'API (/api/profiles/avatars/…) */
  url = input<string | null | undefined>(null);
  size = input(36);

  readonly broken = signal(false);
  readonly src = computed(() => avatarSrc(this.url()));
  readonly initials = computed(() =>
    (this.name() || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('') || '?');

  constructor() {
    // Nouvelle photo (envoi, changement d'utilisateur) : on retente l'affichage
    effect(() => {
      this.url();
      this.broken.set(false);
    });
  }
}
