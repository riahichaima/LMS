import { Component, computed, effect, input, model, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  ModalBodyComponent,
  ModalComponent,
  ModalFooterComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  SpinnerComponent
} from '@coreui/angular';
import { AccountService, DeletionReason } from '../services/account.service';
import { AuthService } from '../services/auth.service';

/**
 * Suppression de mon compte (étudiant / enseignant), en deux étapes :
 *  1. choisir la raison (4 raisons selon le rôle + « Autre raison » à écrire) ;
 *  2. confirmer : la raison est envoyée à l'administration, le compte est supprimé et la session fermée.
 */
@Component({
  selector: 'app-delete-account-dialog',
  standalone: true,
  imports: [FormsModule, ModalComponent, ModalHeaderComponent, ModalTitleDirective, ModalBodyComponent, ModalFooterComponent, SpinnerComponent],
  template: `
    <c-modal [visible]="visible()" (visibleChange)="onVisible($event)" alignment="center" backdrop="static" id="deleteAccountModal">
      <c-modal-header>
        <h5 cModalTitle>{{ step() === 1 ? 'Pourquoi voulez-vous supprimer votre compte ?' : 'Confirmer la suppression' }}</h5>
        <button type="button" class="btn-close" aria-label="Fermer" (click)="close()" [disabled]="deleting()"></button>
      </c-modal-header>

      <c-modal-body>
        @if (loading()) {
          <div class="text-center py-3"><c-spinner size="sm" color="primary" /></div>
        } @else if (step() === 1) {
          <p class="small text-body-secondary">Votre réponse est transmise à l'administration pour améliorer la plateforme.</p>
          <div class="d-flex flex-column gap-2" role="radiogroup" aria-label="Raison de la suppression">
            @for (r of reasons(); track r.code) {
              <label class="reason" [class.reason--active]="reason() === r.code">
                <input type="radio" class="form-check-input me-2" name="deletionReason" [value]="r.code"
                       [checked]="reason() === r.code" (change)="reason.set(r.code)">
                {{ r.label }}
              </label>
            }
          </div>
          @if (reason()) {
            <label class="form-label small mt-3" for="deletionDetails">
              {{ reason() === 'AUTRE' ? 'Écrivez votre raison *' : 'Précisions (facultatif)' }}
            </label>
            <textarea id="deletionDetails" class="form-control" rows="3" maxlength="1000"
                      [placeholder]="reason() === 'AUTRE' ? 'Expliquez pourquoi vous partez…' : 'Dites-nous en plus si vous le souhaitez…'"
                      [ngModel]="details()" (ngModelChange)="details.set($event)"></textarea>
          }
        } @else {
          <div class="alert alert-danger mb-3">
            <strong>Cette action est définitive.</strong> Votre compte et vos données personnelles seront supprimés
            et vous ne pourrez plus vous connecter.
          </div>
          <ul class="small mb-3">
            @if (role() === 'TEACHER') {
              <li>Vos cours qui n'ont jamais été achetés sont supprimés ; les cours déjà vendus sont retirés de la vente
                (les étudiants qui les ont achetés gardent l'accès).</li>
            } @else {
              <li>Vos inscriptions, votre progression et vos messages sont supprimés.</li>
            }
            <li>Raison envoyée : <strong>{{ reasonLabel() }}</strong></li>
            <li>Vous pourrez créer un nouveau compte plus tard avec la même adresse email.</li>
          </ul>
        }
        @if (error()) {
          <div class="alert alert-warning small mt-3 mb-0">{{ error() }}</div>
        }
      </c-modal-body>

      <c-modal-footer>
        @if (step() === 1) {
          <button type="button" class="btn btn-outline-secondary" (click)="close()">Annuler</button>
          <button type="button" class="btn btn-danger" [disabled]="!canContinue()" (click)="next()">Continuer</button>
        } @else {
          <button type="button" class="btn btn-outline-secondary" (click)="step.set(1)" [disabled]="deleting()">Retour</button>
          <button type="button" class="btn btn-danger" (click)="confirm()" [disabled]="deleting()">
            {{ deleting() ? 'Suppression…' : 'Confirmer la suppression' }}
          </button>
        }
      </c-modal-footer>
    </c-modal>
  `,
  styles: [`
    .reason {
      display: flex; align-items: center; cursor: pointer;
      padding: .6rem .8rem; border: 1px solid var(--cui-border-color); border-radius: .5rem;
    }
    .reason:hover { background: var(--cui-tertiary-bg); }
    .reason--active { border-color: var(--cui-danger); background: var(--cui-danger-bg-subtle); }
  `]
})
export class DeleteAccountDialogComponent {
  visible = model(false);
  role = input<string>('');

  reasons = signal<DeletionReason[]>([]);
  reason = signal('');
  details = signal('');
  step = signal<1 | 2>(1);
  loading = signal(false);
  deleting = signal(false);
  error = signal('');

  readonly reasonLabel = computed(() => {
    const label = this.reasons().find(r => r.code === this.reason())?.label ?? '';
    const text = this.details().trim();
    return this.reason() === 'AUTRE' ? text : text ? `${label} — ${text}` : label;
  });
  readonly canContinue = computed(() => !!this.reason() && (this.reason() !== 'AUTRE' || this.details().trim().length >= 5));

  constructor(private account: AccountService, private auth: AuthService, private router: Router) {
    // À chaque ouverture : formulaire vide et raisons du rôle chargées
    effect(() => {
      if (this.visible()) untracked(() => this.open());
    });
  }

  onVisible(open: boolean): void {
    if (!open) this.close();
  }

  private open(): void {
    this.reset();
    this.loading.set(true);
    this.account.deletionReasons().subscribe({
      next: list => { this.reasons.set(list); this.loading.set(false); },
      error: err => { this.loading.set(false); this.error.set(err.error?.message || 'Impossible de charger les raisons.'); }
    });
  }

  next(): void {
    if (!this.canContinue()) {
      this.error.set(this.reason() === 'AUTRE' ? 'Écrivez votre raison (5 caractères minimum).' : 'Choisissez une raison.');
      return;
    }
    this.error.set('');
    this.step.set(2);
  }

  confirm(): void {
    this.deleting.set(true);
    this.error.set('');
    this.account.deleteMyAccount(this.reason(), this.details().trim()).subscribe({
      next: () => {
        this.deleting.set(false);
        this.visible.set(false);
        this.auth.clearSession();
        this.router.navigate(['/login'], { queryParams: { deleted: 1 } });
      },
      error: err => {
        this.deleting.set(false);
        this.error.set(err.error?.message || 'La suppression a échoué. Réessayez plus tard.');
      }
    });
  }

  close(): void {
    if (this.deleting()) return;
    this.visible.set(false);
  }

  private reset(): void {
    this.reason.set('');
    this.details.set('');
    this.step.set(1);
    this.error.set('');
  }
}
