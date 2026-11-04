import { Component, input, signal } from '@angular/core';

/**
 * Bouton « œil » à placer juste après un champ mot de passe (dans un c-input-group) :
 * <input #pwd type="password" … /> <app-password-toggle [input]="pwd" />
 */
@Component({
  selector: 'app-password-toggle',
  standalone: true,
  host: { class: 'input-group-text p-0' },
  template: `
    <button type="button" class="pw-toggle" (click)="toggle()" [disabled]="input().disabled"
            [attr.aria-label]="visible() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'"
            [attr.aria-pressed]="visible()" [title]="visible() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'">
      @if (visible()) {
        <!-- œil barré -->
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
          <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
          <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
          <line x1="1" y1="1" x2="23" y2="23"/>
        </svg>
      } @else {
        <!-- œil -->
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
          <circle cx="12" cy="12" r="3"/>
        </svg>
      }
    </button>
  `,
  styles: [`
    .pw-toggle {
      display: flex; align-items: center; justify-content: center;
      height: 100%; padding: 0 .75rem; border: 0; background: transparent;
      color: var(--cui-secondary-color); cursor: pointer;
    }
    .pw-toggle:hover:not(:disabled), .pw-toggle:focus-visible { color: var(--cui-primary); }
    .pw-toggle:focus-visible { outline: 2px solid rgba(var(--cui-primary-rgb), .45); outline-offset: -2px; border-radius: 6px; }
    .pw-toggle:disabled { opacity: .5; cursor: not-allowed; }
  `]
})
export class PasswordToggleComponent {
  readonly input = input.required<HTMLInputElement>();
  readonly visible = signal(false);

  toggle(): void {
    const el = this.input();
    this.visible.update(v => !v);
    el.type = this.visible() ? 'text' : 'password';
    el.focus();
  }
}
