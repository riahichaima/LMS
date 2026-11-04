import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonDirective, CardBodyComponent, CardComponent, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';
import { PaymentService } from '../../services/payment.service';

// Retour depuis « Retour / Annuler » sur Stripe Checkout : la session est fermée côté serveur
@Component({
  selector: 'app-payment-cancel',
  standalone: true,
  imports: [CommonModule, RouterLink, RowComponent, ColComponent, CardComponent, CardBodyComponent, ButtonDirective, SpinnerComponent],
  templateUrl: './payment-cancel.component.html'
})
export class PaymentCancelComponent implements OnInit {
  loading = signal(true);
  courseTitle = signal('');
  // Si le paiement a malgré tout abouti juste avant l'annulation
  alreadyPaid = signal(false);

  constructor(private route: ActivatedRoute, private paymentService: PaymentService) {}

  ngOnInit(): void {
    const sessionId = this.route.snapshot.queryParamMap.get('session_id');
    if (!sessionId) {
      this.loading.set(false);
      return;
    }
    this.paymentService.cancelSession(sessionId).subscribe({
      next: (s) => {
        this.loading.set(false);
        this.courseTitle.set(s.courseTitle);
        this.alreadyPaid.set(s.status === 'PAID');
      },
      error: () => this.loading.set(false)
    });
  }
}
