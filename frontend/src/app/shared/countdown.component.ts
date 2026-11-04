import { Component, DestroyRef, OnChanges, computed, inject, input, output, signal } from '@angular/core';

/**
 * Chrono d'un quiz / TP minuté. L'heure de fin vient du serveur (endsAt) ; le décalage entre l'horloge
 * du serveur et celle du navigateur est corrigé avec serverNow. Émet `expired` une seule fois à 0.
 */
@Component({
  selector: 'app-countdown',
  standalone: true,
  template: `
    <div class="countdown" [class.countdown--warn]="level() === 'warn'" [class.countdown--danger]="level() === 'danger'"
         role="timer" [attr.aria-label]="'Temps restant : ' + label()">
      <span class="countdown__label">Temps restant</span>
      <strong class="countdown__time">{{ label() }}</strong>
      <div class="countdown__bar"><span [style.width.%]="percent()"></span></div>
    </div>
  `,
  styles: [`
    :host { display: block; position: sticky; top: .5rem; z-index: 5; }
    .countdown {
      display: grid; grid-template-columns: auto 1fr; align-items: center; gap: .15rem 1rem;
      padding: .65rem 1rem; border-radius: 14px; background: var(--cui-body-bg);
      border: 1px solid var(--cui-border-color); box-shadow: 0 6px 20px rgba(21, 101, 224, .10);
    }
    .countdown__label { font-size: .75rem; text-transform: uppercase; letter-spacing: .05em; color: var(--cui-secondary-color); }
    .countdown__time { grid-row: span 2; justify-self: end; font-size: 1.6rem; font-variant-numeric: tabular-nums; color: var(--cui-primary); }
    .countdown__bar { height: 6px; border-radius: 6px; background: var(--cui-tertiary-bg); overflow: hidden; }
    .countdown__bar span { display: block; height: 100%; background: var(--cui-primary); transition: width 1s linear; }
    .countdown--warn .countdown__time { color: var(--cui-warning); }
    .countdown--warn .countdown__bar span { background: var(--cui-warning); }
    .countdown--danger { border-color: var(--cui-danger); }
    .countdown--danger .countdown__time { color: var(--cui-danger); }
    .countdown--danger .countdown__bar span { background: var(--cui-danger); }
  `]
})
export class CountdownComponent implements OnChanges {
  readonly startedAt = input.required<string>();
  readonly endsAt = input.required<string>();
  readonly serverNow = input.required<string>();
  readonly expired = output<void>();

  private readonly remaining = signal(0);
  private offset = 0;
  private fired = false;
  private timer?: ReturnType<typeof setInterval>;

  readonly label = computed(() => {
    const s = Math.max(0, this.remaining());
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    const mm = String(m).padStart(2, '0');
    const ss = String(sec).padStart(2, '0');
    return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  });

  readonly percent = computed(() => {
    const total = (Date.parse(this.endsAt()) - Date.parse(this.startedAt())) / 1000;
    return total > 0 ? Math.max(0, Math.min(100, (this.remaining() / total) * 100)) : 0;
  });

  readonly level = computed(() => (this.remaining() <= 60 ? 'danger' : this.remaining() <= 300 ? 'warn' : 'ok'));

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));
  }

  ngOnChanges(): void {
    // Horloge du serveur = horloge locale + offset
    this.offset = Date.parse(this.serverNow()) - Date.now();
    this.fired = false;
    clearInterval(this.timer);
    this.tick();
    this.timer = setInterval(() => this.tick(), 1000);
  }

  private tick(): void {
    const left = Math.round((Date.parse(this.endsAt()) - (Date.now() + this.offset)) / 1000);
    this.remaining.set(Math.max(0, left));
    if (left <= 0 && !this.fired) {
      this.fired = true;
      clearInterval(this.timer);
      this.expired.emit();
    }
  }
}
