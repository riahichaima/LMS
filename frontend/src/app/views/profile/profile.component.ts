import { Component, ElementRef, OnInit, ViewChild, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  ColComponent,
  ProgressComponent,
  RowComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../services/auth.service';
import { ProfileService, ProfileView, ROLE_LABELS } from '../../services/profile.service';
import { AvatarComponent } from '../../shared/avatar.component';
import { StarRatingComponent } from '../../shared/star-rating.component';
import { TeacherRatingComponent } from '../../shared/teacher-rating.component';
import { DeleteAccountDialogComponent } from '../../shared/delete-account-dialog.component';

const MAX_AVATAR_BYTES = 3 * 1024 * 1024;

/**
 * Profil d'un utilisateur, adapté à son rôle :
 *  - tous : photo, prénom, nom, rôle, ancienneté (photo et nom modifiables sur son propre profil) ;
 *  - étudiant : formateurs et cours suivis avec la progression ;
 *  - formateur : étudiants, cours, évaluation en % et notes des cours (avis réels des étudiants) ;
 *  - administrateur / super administrateur : nom et rôle.
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, AlertComponent, BadgeComponent, CardComponent, CardBodyComponent,
    CardHeaderComponent, ColComponent, ProgressComponent, RowComponent, SpinnerComponent, IconDirective,
    AvatarComponent, StarRatingComponent, TeacherRatingComponent, DeleteAccountDialogComponent
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  profile = signal<ProfileView | null>(null);
  loading = signal(true);
  error = signal('');

  editing = signal(false);
  firstName = signal('');
  lastName = signal('');
  saving = signal(false);
  uploading = signal(false);
  photoError = signal('');
  notice = signal('');

  /** Fenêtre « Supprimer mon compte » (étudiant / enseignant, sur son propre profil) */
  deleteDialog = signal(false);

  readonly roleLabels = ROLE_LABELS;
  readonly isAdminRole = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(this.profile()?.role ?? ''));

  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  constructor(private route: ActivatedRoute, private profiles: ProfileService, public authService: AuthService) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = Number(params.get('id'));
      this.load(id || null);
    });
  }

  private load(id: number | null): void {
    this.loading.set(true);
    this.error.set('');
    this.editing.set(false);
    const request = id && id !== this.authService.currentUserSignal()?.id ? this.profiles.get(id) : this.profiles.getMe();
    request.subscribe({
      next: p => { this.profile.set(p); this.loading.set(false); },
      error: err => {
        this.loading.set(false);
        this.error.set(err.status === 403 ? (err.error?.message || "Vous n'avez pas accès à ce profil.") : (err.error?.message || 'Profil introuvable.'));
      }
    });
  }

  // ── Mon profil : prénom / nom ──
  startEdit(): void {
    const p = this.profile();
    if (!p) return;
    this.firstName.set(p.firstName ?? '');
    this.lastName.set(p.lastName ?? '');
    this.notice.set('');
    this.editing.set(true);
  }

  saveNames(): void {
    if (!this.firstName().trim()) {
      this.error.set('Le prénom est obligatoire.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.profiles.updateNames(this.firstName().trim(), this.lastName().trim()).subscribe({
      next: p => { this.saving.set(false); this.profile.set(p); this.editing.set(false); this.notice.set('Profil mis à jour.'); },
      error: err => { this.saving.set(false); this.error.set(err.error?.message || 'Modification impossible.'); }
    });
  }

  // ── Mon profil : photo ──
  pickPhoto(): void {
    this.photoError.set('');
    this.fileInput?.nativeElement.click();
  }

  onPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!/^image\/(jpeg|png|gif|webp)$/.test(file.type)) {
      this.photoError.set('Choisissez une image JPG, PNG, GIF ou WebP.');
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      this.photoError.set('Image trop lourde : 3 Mo maximum.');
      return;
    }
    this.uploading.set(true);
    this.profiles.uploadAvatar(file).subscribe({
      next: p => { this.uploading.set(false); this.profile.set(p); this.notice.set('Photo de profil mise à jour.'); },
      error: err => { this.uploading.set(false); this.photoError.set(err.error?.message || "L'image n'a pas pu être envoyée."); }
    });
  }

  removePhoto(): void {
    if (!confirm('Retirer votre photo de profil ?')) return;
    this.uploading.set(true);
    this.profiles.deleteAvatar().subscribe({
      next: p => { this.uploading.set(false); this.profile.set(p); this.notice.set('Photo retirée.'); },
      error: () => this.uploading.set(false)
    });
  }

  // ── Affichage ──
  /** « depuis 3 mois », « depuis 12 jours »… */
  seniority(iso: string | null): string {
    if (!iso) return '';
    // Jours de calendrier (inscrit hier soir = « 1 jour », cohérent avec la date affichée)
    const start = new Date(iso);
    start.setHours(0, 0, 0, 0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const days = Math.round((today.getTime() - start.getTime()) / 86400000);
    if (days < 1) return "depuis aujourd'hui";
    if (days < 31) return `depuis ${days} jour${days > 1 ? 's' : ''}`;
    const months = Math.floor(days / 30.44);
    if (months < 12) return `depuis ${months} mois`;
    const years = Math.floor(months / 12);
    return `depuis ${years} an${years > 1 ? 's' : ''}`;
  }

  pct(count: number, total: number): number {
    return total ? Math.round(count * 100 / total) : 0;
  }
}
