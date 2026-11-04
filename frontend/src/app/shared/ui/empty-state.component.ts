import { Component, input } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';

/** État vide commun : icône, titre, explication et action facultative (contenu projeté). */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [IconDirective],
  template: `
    <div class="es" role="status">
      <span class="es__icon" aria-hidden="true"><svg cIcon [name]="icon()"></svg></span>
      <p class="es__title">{{ title() }}</p>
      @if (text()) { <p class="es__text">{{ text() }}</p> }
      <div class="es__action"><ng-content /></div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .es { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 2rem 1rem; }
    .es__icon {
      display: grid; place-items: center;
      width: 56px; height: 56px; margin-bottom: .75rem; border-radius: 16px;
      background: var(--lms-gradient-soft, rgba(var(--cui-primary-rgb), .08)); color: var(--cui-primary);
    }
    .es__icon svg { width: 26px; height: 26px; }
    .es__title { margin: 0; font-weight: 600; color: var(--cui-emphasis-color); }
    .es__text { margin: .3rem 0 0; max-width: 420px; font-size: .88rem; color: var(--cui-secondary-color); }
    .es__action:not(:empty) { margin-top: 1rem; }
  `]
})
export class EmptyStateComponent {
  icon = input('cilInbox');
  title = input.required<string>();
  text = input<string | null>(null);
}
