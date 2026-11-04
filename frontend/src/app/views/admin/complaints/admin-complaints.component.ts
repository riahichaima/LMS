import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AlertComponent, SpinnerComponent } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import {
  COMPLAINT_CATEGORIES, COMPLAINT_STATUS, ComplaintService, ComplaintStatus, ComplaintView, categoryLabel
} from '../../../services/complaint.service';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { AvatarComponent } from '../../../shared/avatar.component';
import { RoleLabelPipe } from '../../../shared/ui/labels';
import { normalizeText } from '../../../shared/ui/labels';

/** Admin / super admin : traiter les réclamations (en cours, résolue, rejetée) et répondre à l'auteur. */
@Component({
  selector: 'app-admin-complaints',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertComponent, SpinnerComponent, IconDirective,
    PageHeaderComponent, StatCardComponent, EmptyStateComponent, AvatarComponent, RoleLabelPipe],
  templateUrl: './admin-complaints.component.html',
  styleUrl: './admin-complaints.component.scss'
})
export class AdminComplaintsComponent implements OnInit {
  readonly categories = COMPLAINT_CATEGORIES;
  readonly status = COMPLAINT_STATUS;
  readonly categoryLabel = categoryLabel;

  complaints = signal<ComplaintView[]>([]);
  loading = signal(true);
  error = signal('');
  success = signal('');

  statusFilter = signal<'' | ComplaintStatus>('');
  categoryFilter = signal('');
  search = signal('');

  selectedId = signal<number | null>(null);
  response = '';
  saving = signal(false);

  readonly counts = computed(() => {
    const list = this.complaints();
    const n = (s: ComplaintStatus) => list.filter(c => c.status === s).length;
    return { OPEN: n('OPEN'), IN_PROGRESS: n('IN_PROGRESS'), RESOLVED: n('RESOLVED'), REJECTED: n('REJECTED') };
  });

  readonly filtered = computed(() => {
    const q = normalizeText(this.search());
    return this.complaints().filter(c =>
      (!this.statusFilter() || c.status === this.statusFilter())
      && (!this.categoryFilter() || c.category === this.categoryFilter())
      && (!q || normalizeText(`${c.subject} ${c.message} ${c.authorName} ${c.authorEmail}`).includes(q)));
  });

  readonly selected = computed(() => this.complaints().find(c => c.id === this.selectedId()) ?? null);

  constructor(private complaintService: ComplaintService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.queryParamMap.get('id')) || null;
    this.complaintService.all().subscribe({
      next: list => {
        this.complaints.set(list);
        this.loading.set(false);
        this.select(id && list.some(c => c.id === id) ? id : (list.find(c => c.status === 'OPEN') ?? list[0])?.id ?? null);
      },
      error: () => { this.loading.set(false); this.error.set('Impossible de charger les réclamations.'); }
    });
  }

  select(id: number | null): void {
    this.selectedId.set(id);
    this.response = this.complaints().find(c => c.id === id)?.adminResponse ?? '';
  }

  handle(status: ComplaintStatus): void {
    const c = this.selected();
    if (!c) return;
    this.error.set('');
    this.success.set('');
    this.saving.set(true);
    this.complaintService.handle(c.id, status, this.response.trim()).subscribe({
      next: updated => {
        this.saving.set(false);
        this.complaints.update(list => list.map(x => (x.id === updated.id ? updated : x)));
        this.success.set(`Réclamation « ${updated.subject} » : ${this.status[updated.status].label.toLowerCase()}. L'auteur a été notifié.`);
      },
      error: err => {
        this.saving.set(false);
        this.error.set(err.error?.message || 'Mise à jour impossible.');
      }
    });
  }
}
