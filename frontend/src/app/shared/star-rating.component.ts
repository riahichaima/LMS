import { Component, computed, input, output, signal } from '@angular/core';

/**
 * Étoiles de notation. Lecture seule par défaut (moyenne, avec demi-étoiles) ;
 * avec [editable]="true", l'utilisateur choisit 1 à 5 étoiles (clic ou clavier).
 */
@Component({
  selector: 'app-star-rating',
  standalone: true,
  template: `
    <span class="stars" [class.editable]="editable()" [style.font-size.px]="size()"
          [attr.role]="editable() ? 'radiogroup' : 'img'"
          [attr.aria-label]="editable() ? 'Choisir une note' : (value() !== null ? value() + ' sur 5' : 'Pas encore noté')">
      @for (i of positions; track i) {
        @if (editable()) {
          <button type="button" class="star-btn" role="radio" [attr.aria-checked]="value() === i" [attr.aria-label]="i + ' étoile' + (i > 1 ? 's' : '')"
                  (mouseenter)="hover.set(i)" (mouseleave)="hover.set(0)" (focus)="hover.set(i)" (blur)="hover.set(0)" (click)="pick.emit(i)">
            <span class="star" [class.on]="i <= shown()">★</span>
          </button>
        } @else {
          <span class="star-wrap" aria-hidden="true">
            <span class="star">★</span>
            <span class="star on fill" [style.width.%]="fill(i)">★</span>
          </span>
        }
      }
    </span>
  `,
  styles: [`
    .stars { display: inline-flex; align-items: center; line-height: 1; gap: 1px; white-space: nowrap; }
    .star { color: var(--cui-border-color); }
    .star.on { color: #f5a524; }
    .star-wrap { position: relative; display: inline-block; }
    .star-wrap .fill { position: absolute; left: 0; top: 0; overflow: hidden; }
    .star-btn { border: 0; background: transparent; padding: 0 2px; cursor: pointer; line-height: 1; font-size: inherit; }
    .star-btn:focus-visible { outline: 2px solid var(--cui-primary); border-radius: 4px; }
    .editable .star-btn:hover .star { transform: scale(1.15); }
    .star-btn .star { display: inline-block; transition: transform .1s; }
  `]
})
export class StarRatingComponent {
  value = input<number | null>(null);
  editable = input(false);
  size = input(16);
  pick = output<number>();

  readonly positions = [1, 2, 3, 4, 5];
  readonly hover = signal(0);
  readonly shown = computed(() => this.hover() || this.value() || 0);

  /** Remplissage de l'étoile i en % (demi-étoiles pour les moyennes). */
  fill(i: number): number {
    const v = this.value() ?? 0;
    return Math.max(0, Math.min(1, v - (i - 1))) * 100;
  }
}
