import { Component, input } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';

export type StatTone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/**
 * Carte de chiffre clé (KPI) commune aux tableaux de bord : libellé, valeur, précision et icône.
 * Contenu projeté sous la valeur (barre de progression, détail…).
 */
@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [IconDirective],
  template: `
    <div class="sc" [class]="'sc sc--' + tone()" [class.sc--highlight]="highlight()">
      <div class="sc__head">
        <span class="sc__label">{{ label() }}</span>
        @if (icon()) {
          <span class="sc__icon" aria-hidden="true"><svg cIcon [name]="icon()!"></svg></span>
        }
      </div>
      <div class="sc__value">{{ value() }}</div>
      <ng-content />
      @if (hint()) { <div class="sc__hint">{{ hint() }}</div> }
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }
    .sc {
      --tone: var(--cui-primary-rgb);
      height: 100%;
      padding: 1rem 1.1rem;
      border-radius: var(--lms-radius, 14px);
      background: var(--cui-body-bg);
      border: 1px solid var(--lms-border, var(--cui-border-color));
      box-shadow: var(--lms-shadow, none);
      transition: box-shadow .2s ease, transform .2s ease;
    }
    .sc:hover { box-shadow: var(--lms-shadow-hover, none); transform: translateY(-1px); }
    .sc--success { --tone: var(--cui-success-rgb); }
    .sc--warning { --tone: var(--cui-warning-rgb); }
    .sc--danger { --tone: var(--cui-danger-rgb); }
    .sc--info { --tone: var(--cui-info-rgb); }
    .sc--neutral { --tone: var(--cui-secondary-rgb); }
    .sc--highlight {
      border-color: rgba(var(--tone), .35);
      background: linear-gradient(135deg, rgba(var(--tone), .08), rgba(var(--tone), .02)), var(--cui-body-bg);
    }
    .sc__head { display: flex; align-items: center; justify-content: space-between; gap: .5rem; }
    .sc__label { font-size: .78rem; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; color: var(--cui-secondary-color); }
    .sc__icon {
      display: grid; place-items: center; flex-shrink: 0;
      width: 34px; height: 34px; border-radius: 10px;
      background: rgba(var(--tone), .12); color: rgb(var(--tone));
    }
    .sc__icon svg { width: 17px; height: 17px; }
    .sc__value { margin-top: .35rem; font-size: 1.65rem; font-weight: 700; line-height: 1.2; color: rgb(var(--tone)); letter-spacing: -.01em; }
    .sc--neutral .sc__value { color: var(--cui-emphasis-color); }
    .sc__hint { margin-top: .35rem; font-size: .8rem; color: var(--cui-secondary-color); }
  `]
})
export class StatCardComponent {
  label = input.required<string>();
  value = input<string | number | null>('—');
  hint = input<string | null>(null);
  icon = input<string | null>(null);
  tone = input<StatTone>('primary');
  /** Carte mise en avant (fond teinté) : solde disponible, demandes en attente… */
  highlight = input(false);
}
