import { Component, computed, input, output } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { AttemptState, formatDue, formatDuration } from '../services/assessment-schedule.service';

/** Écran avant un quiz / TP minuté (ou fermé) : règles, date limite et bouton « Commencer ». */
@Component({
  selector: 'app-attempt-gate',
  standalone: true,
  imports: [IconDirective],
  template: `
    <section class="gate">
      <div class="gate__icon"><svg cIcon [name]="closed() ? 'cilBan' : 'cilClock'" size="xl"></svg></div>
      <h2 class="gate__title">{{ state().title }}</h2>

      @if (closed()) {
        <p class="gate__lead">La date limite de rendu ({{ due() }}) est dépassée : ce {{ kindLabel() }} n'est plus disponible.</p>
      } @else {
        <ul class="gate__rules">
          @if (state().durationMinutes) {
            <li><svg cIcon name="cilClock" size="sm"></svg> Durée : <strong>{{ duration() }}</strong> dès que tu cliques sur « Commencer ».</li>
          }
          @if (state().dueAt) {
            <li><svg cIcon name="cilCalendar" size="sm"></svg> À rendre avant le <strong>{{ due() }}</strong>.</li>
          }
          <li><svg cIcon name="cilWarning" size="sm"></svg> Le chrono continue même si tu quittes ou recharges la page.</li>
          <li><svg cIcon name="cilSend" size="sm"></svg> Tes réponses sont enregistrées au fur et à mesure et <strong>envoyées automatiquement</strong> à la fin du temps.</li>
          <li><svg cIcon name="cilLockLocked" size="sm"></svg> Un seul passage.</li>
        </ul>
        <button type="button" class="btn btn-primary btn-lg" (click)="start.emit()" [disabled]="busy()">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> }
          Commencer le {{ kindLabel() }}
        </button>
      }
    </section>
  `,
  styles: [`
    .gate {
      max-width: 620px; margin: 1.5rem auto; padding: 2rem; text-align: center; border-radius: 18px;
      background: linear-gradient(160deg, rgba(21, 101, 224, .08), rgba(31, 162, 255, .03));
      border: 1px solid var(--cui-border-color);
    }
    .gate__icon {
      width: 64px; height: 64px; margin: 0 auto 1rem; border-radius: 50%; display: grid; place-items: center;
      background: var(--cui-primary); color: #fff;
    }
    .gate__title { font-size: 1.4rem; margin-bottom: 1rem; }
    .gate__lead { color: var(--cui-secondary-color); }
    .gate__rules { list-style: none; padding: 0; margin: 0 auto 1.5rem; text-align: left; display: grid; gap: .55rem; max-width: 480px; }
    .gate__rules li { display: flex; gap: .55rem; align-items: baseline; }
    .gate__rules svg { color: var(--cui-primary); flex-shrink: 0; }
  `]
})
export class AttemptGateComponent {
  readonly state = input.required<AttemptState>();
  readonly kindLabel = input('quiz');
  readonly busy = input(false);
  readonly start = output<void>();

  readonly closed = computed(() => this.state().closed && !this.state().started);
  readonly due = computed(() => formatDue(this.state().dueAt));
  readonly duration = computed(() => formatDuration(this.state().durationMinutes));
}
