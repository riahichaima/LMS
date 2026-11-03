import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  AssessmentKind, AssessmentScheduleService, ScheduleRules, formatDue, formatDuration, fromLocalInput, toLocalInput
} from '../services/assessment-schedule.service';

/**
 * Enseignant : date limite de rendu (calendrier) et temps de travail d'un quiz / TP,
 * affichés en pastilles et modifiables sur place.
 */
@Component({
  selector: 'app-schedule-editor',
  standalone: true,
  imports: [FormsModule, IconDirective],
  template: `
    <div class="sched">
      @if (!editing()) {
        <span class="sched__chip" [class.sched__chip--muted]="!dueAt()" [class.sched__chip--closed]="isPast()">
          <svg cIcon name="cilCalendar" size="sm"></svg>
          {{ dueAt() ? (isPast() ? 'Fermé le ' : 'Avant le ') + formatDue(dueAt()) : 'Sans date limite' }}
        </span>
        <span class="sched__chip" [class.sched__chip--muted]="!durationMinutes()">
          <svg cIcon name="cilClock" size="sm"></svg>
          {{ durationMinutes() ? formatDuration(durationMinutes()) : 'Non minuté' }}
        </span>
        <button type="button" class="btn btn-link btn-sm p-0" (click)="open()">Modifier</button>
      } @else {
        <div class="sched__form">
          <div>
            <label class="form-label small mb-1" [for]="'due-' + kind() + id()">Date limite</label>
            <input type="datetime-local" class="form-control form-control-sm" [id]="'due-' + kind() + id()" [(ngModel)]="dueInput" />
          </div>
          <div>
            <label class="form-label small mb-1" [for]="'dur-' + kind() + id()">Durée (min)</label>
            <input type="number" min="1" max="1440" class="form-control form-control-sm" placeholder="Non minuté"
                   [id]="'dur-' + kind() + id()" [(ngModel)]="durationInput" />
          </div>
          <div class="sched__actions">
            <button type="button" class="btn btn-sm btn-primary" (click)="save()" [disabled]="saving()">
              {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
            </button>
            <button type="button" class="btn btn-sm btn-outline-secondary" (click)="editing.set(false)">Annuler</button>
          </div>
          @if (error()) { <div class="small text-danger w-100">{{ error() }}</div> }
          <div class="small text-body-secondary w-100">Champs vides = sans limite. Une nouvelle date limite est envoyée aux étudiants inscrits.</div>
        </div>
      }
    </div>
  `,
  styles: [`
    .sched { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem .5rem; font-size: .78rem; }
    .sched__chip {
      display: inline-flex; align-items: center; gap: 4px; padding: .1rem .55rem; border-radius: 999px;
      color: var(--cui-primary); background: rgba(var(--cui-primary-rgb), .09);
    }
    .sched__chip svg { width: .75rem; height: .75rem; }
    .sched__chip--muted { color: var(--cui-secondary-color); background: var(--cui-tertiary-bg); }
    .sched__chip--closed { color: var(--cui-danger); background: rgba(var(--cui-danger-rgb), .08); }
    .sched__form {
      display: flex; flex-wrap: wrap; align-items: flex-end; gap: .5rem; width: 100%;
      padding: .6rem; border-radius: 10px; background: var(--cui-tertiary-bg);
    }
    .sched__form > div:not(.sched__actions):not(.small) { flex: 1 1 160px; }
    .sched__actions { display: flex; gap: .35rem; }
  `]
})
export class ScheduleEditorComponent {
  readonly kind = input.required<AssessmentKind>();
  readonly id = input.required<number>();
  readonly dueAt = input<string | null | undefined>(null);
  readonly durationMinutes = input<number | null | undefined>(null);
  readonly changed = output<ScheduleRules>();

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  dueInput = '';
  durationInput: number | null = null;

  readonly formatDue = formatDue;
  readonly formatDuration = formatDuration;

  constructor(private service: AssessmentScheduleService) {}

  isPast(): boolean {
    const d = this.dueAt();
    return !!d && new Date(d).getTime() < Date.now();
  }

  open(): void {
    this.dueInput = toLocalInput(this.dueAt());
    this.durationInput = this.durationMinutes() ?? null;
    this.error.set('');
    this.editing.set(true);
  }

  save(): void {
    const duration = this.durationInput ? Math.round(this.durationInput) : null;
    if (duration != null && (duration < 1 || duration > 1440)) {
      this.error.set('La durée doit être comprise entre 1 et 1440 minutes.');
      return;
    }
    const rules: ScheduleRules = { dueAt: fromLocalInput(this.dueInput), durationMinutes: duration };
    this.saving.set(true);
    this.service.update(this.kind(), this.id(), rules).subscribe({
      next: saved => {
        this.saving.set(false);
        this.editing.set(false);
        this.changed.emit({ dueAt: saved.dueAt, durationMinutes: saved.durationMinutes });
      },
      error: err => {
        this.saving.set(false);
        this.error.set(err.error?.message || "L'enregistrement a échoué.");
      }
    });
  }
}
