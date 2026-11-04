import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonDirective, CardBodyComponent, CardComponent, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { PaymentService, PaymentStatusView } from '../../services/payment.service';

/**
 * Retour de Stripe après paiement. La redirection seule ne donne AUCUN accès : la page interroge le
 * backend, qui ne confirme qu'après le webhook signé ou sa propre vérification auprès de Stripe.
 */
@Component({
  selector: 'app-payment-success',
  standalone: true,
  imports: [CommonModule, RouterLink, RowComponent, ColComponent, CardComponent, CardBodyComponent, ButtonDirective, SpinnerComponent, IconDirective],
  templateUrl: './payment-success.component.html'
})
export class PaymentSuccessComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  private timer?: ReturnType<typeof setTimeout>;
  private attempts = 0;
  private static readonly MAX_ATTEMPTS = 20; // ~40 secondes

  status = signal<PaymentStatusView | null>(null);
  state = signal<'checking' | 'confirmed' | 'failed' | 'waiting' | 'error'>('checking');
  errorMessage = signal('');

  constructor(private route: ActivatedRoute, private paymentService: PaymentService) {
    this.destroyRef.onDestroy(() => clearTimeout(this.timer));
  }

  ngOnInit(): void {
    const sessionId = this.route.snapshot.queryParamMap.get('session_id');
    if (!sessionId) {
      this.state.set('error');
      this.errorMessage.set('Référence de paiement absente.');
      return;
    }
    this.check(sessionId);
  }

  private check(sessionId: string): void {
    this.paymentService.getSessionStatus(sessionId).subscribe({
      next: (s) => {
        this.status.set(s);
        if (s.status === 'PAID' && s.enrolled) {
          this.state.set('confirmed');
        } else if (['FAILED', 'EXPIRED', 'CANCELLED', 'REFUNDED'].includes(s.status)) {
          this.state.set('failed');
        } else if (++this.attempts < PaymentSuccessComponent.MAX_ATTEMPTS) {
          // Confirmation Stripe pas encore reçue : nouvelle vérification dans 2 s
          this.timer = setTimeout(() => this.check(sessionId), 2000);
        } else {
          this.state.set('waiting');
        }
      },
      error: (err) => {
        this.state.set('error');
        this.errorMessage.set(err.error?.message || 'Impossible de vérifier le paiement.');
      }
    });
  }
}
