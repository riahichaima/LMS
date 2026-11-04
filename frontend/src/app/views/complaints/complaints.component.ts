import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AlertComponent, SpinnerComponent } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import {
  COMPLAINT_CATEGORIES, COMPLAINT_STATUS, ComplaintService, ComplaintView, categoryLabel
} from '../../services/complaint.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

/** Étudiant / enseignant : signaler un problème ou faire une réclamation, et suivre la réponse de l'administration. */
@Component({
  selector: 'app-complaints',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertComponent, SpinnerComponent, IconDirective, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './complaints.component.html',
  styleUrl: './complaints.component.scss'
})
export class ComplaintsComponent implements OnInit {
  readonly categories = COMPLAINT_CATEGORIES;
  readonly status = COMPLAINT_STATUS;
  readonly categoryLabel = categoryLabel;

  category = '';
  subject = '';
  message = '';
  sending = signal(false);
  error = signal('');
  success = signal('');

  complaints = signal<ComplaintView[]>([]);
  loading = signal(true);
  highlightId = signal<number | null>(null);

  constructor(private complaintService: ComplaintService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.highlightId.set(Number(this.route.snapshot.queryParamMap.get('id')) || null);
    this.load();
  }

  load(): void {
    this.complaintService.mine().subscribe({
      next: list => { this.complaints.set(list); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('Impossible de charger vos réclamations.'); }
    });
  }

  send(): void {
    this.error.set('');
    this.success.set('');
    if (!this.category) { this.error.set('Choisissez le type de problème.'); return; }
    if (this.subject.trim().length < 3) { this.error.set('Donnez un objet d\'au moins 3 caractères.'); return; }
    if (this.message.trim().length < 10) { this.error.set('Décrivez le problème en au moins 10 caractères.'); return; }
    this.sending.set(true);
    this.complaintService.create(this.category, this.subject.trim(), this.message.trim()).subscribe({
      next: c => {
        this.sending.set(false);
        this.success.set('Votre réclamation a été envoyée à l\'administration. Vous serez notifié de sa réponse.');
        this.complaints.update(list => [c, ...list]);
        this.category = '';
        this.subject = '';
        this.message = '';
      },
      error: err => {
        this.sending.set(false);
        this.error.set(err.error?.message || 'Envoi impossible, réessayez.');
      }
    });
  }
}
