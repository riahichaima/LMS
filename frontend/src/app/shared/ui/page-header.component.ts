import { Component, input } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';

/**
 * En-tête commun des pages des espaces connectés : icône, titre, sous-titre et actions (contenu projeté).
 * Exemple : <app-page-header title="Mes revenus" subtitle="…" icon="cilDollar"><button>…</button></app-page-header>
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [IconDirective],
  template: `
    <header class="ph">
      <div class="ph__main">
        @if (icon()) {
          <span class="ph__icon" aria-hidden="true"><svg cIcon [name]="icon()!"></svg></span>
        }
        <div class="ph__text">
          <h1 class="ph__title">{{ title() }}</h1>
          @if (subtitle()) { <p class="ph__subtitle">{{ subtitle() }}</p> }
        </div>
      </div>
      <div class="ph__actions"><ng-content /></div>
    </header>
  `,
  styles: [`
    :host { display: block; margin-bottom: 1.25rem; }
    .ph { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .75rem 1rem; }
    .ph__main { display: flex; align-items: center; gap: .85rem; min-width: 0; }
    .ph__icon {
      display: grid; place-items: center; flex-shrink: 0;
      width: 44px; height: 44px; border-radius: 12px;
      background: var(--lms-gradient-soft, rgba(var(--cui-primary-rgb), .1));
      color: var(--cui-primary);
      box-shadow: inset 0 0 0 1px rgba(var(--cui-primary-rgb), .14);
    }
    .ph__icon svg { width: 22px; height: 22px; }
    .ph__text { min-width: 0; }
    .ph__title { margin: 0; font-size: 1.35rem; font-weight: 700; letter-spacing: -.01em; color: var(--cui-emphasis-color); }
    .ph__subtitle { margin: .15rem 0 0; color: var(--cui-secondary-color); font-size: .9rem; }
    .ph__actions { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; }
    .ph__actions:empty { display: none; }
    @media (max-width: 576px) {
      .ph__title { font-size: 1.15rem; }
      .ph__icon { width: 38px; height: 38px; }
    }
  `]
})
export class PageHeaderComponent {
  title = input.required<string>();
  subtitle = input<string | null>(null);
  icon = input<string | null>(null);
}
