import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CardComponent,
  CardBodyComponent,
  SpinnerComponent,
  AlertComponent,
  FormSelectDirective,
  ModalComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  ModalBodyComponent,
  ModalFooterComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../../services/auth.service';
import { AdminService, UserWithRoleDetails } from '../../../services/admin.service';
import { AvatarComponent } from '../../../shared/avatar.component';
import { normalizeText, roleCode } from '../../../shared/ui/labels';

import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { RoleLabelPipe, RoleTonePipe } from '../../../shared/ui/labels';
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    CardBodyComponent,
    SpinnerComponent,
    AlertComponent,
    FormSelectDirective,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    ModalFooterComponent,
    IconDirective,
    AvatarComponent,
    PageHeaderComponent,
    EmptyStateComponent,
    RoleLabelPipe,
    RoleTonePipe
  ],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss'
})
export class AdminUsersComponent implements OnInit {
  users = signal<UserWithRoleDetails[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  selectedUser: UserWithRoleDetails | null = null;
  selectedRole = '';
  roleModalVisible = signal(false);

  readonly isSuperAdmin: boolean;

  // ── Recherche, filtres et pagination (dans le navigateur, l'API ne change pas) ──
  readonly pageSize = 10;
  search = signal('');
  roleFilter = signal('');
  statusFilter = signal<'' | 'active' | 'inactive'>('');
  page = signal(1);

  /** Nombre de comptes par rôle (pastilles de filtre) */
  readonly roleCounts = computed(() => {
    const counts: Record<string, number> = {};
    for (const u of this.users()) counts[roleCode(u.role)] = (counts[roleCode(u.role)] ?? 0) + 1;
    return counts;
  });

  readonly filteredUsers = computed(() => {
    const q = normalizeText(this.search());
    const role = this.roleFilter();
    const status = this.statusFilter();
    return this.users().filter(u => {
      if (q && !normalizeText(`${u.name} ${u.email}`).includes(q)) return false;
      if (role && roleCode(u.role) !== role) return false;
      if (status === 'active' && u.enabled === false) return false;
      if (status === 'inactive' && u.enabled !== false) return false;
      return true;
    });
  });

  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filteredUsers().length / this.pageSize)));
  readonly pagedUsers = computed(() => {
    const page = Math.min(this.page(), this.pageCount());
    return this.filteredUsers().slice((page - 1) * this.pageSize, page * this.pageSize);
  });
  readonly pages = computed(() => Array.from({ length: this.pageCount() }, (_, i) => i + 1));
  readonly rangeLabel = computed(() => {
    const total = this.filteredUsers().length;
    if (!total) return '0 utilisateur';
    const page = Math.min(this.page(), this.pageCount());
    const from = (page - 1) * this.pageSize + 1;
    return `${from}–${Math.min(page * this.pageSize, total)} sur ${total}`;
  });

  setSearch(value: string): void { this.search.set(value); this.page.set(1); }
  setRole(value: string): void { this.roleFilter.set(value); this.page.set(1); }
  setStatus(value: '' | 'active' | 'inactive'): void { this.statusFilter.set(value); this.page.set(1); }
  goTo(page: number): void { this.page.set(Math.min(Math.max(1, page), this.pageCount())); }
  resetFilters(): void { this.search.set(''); this.roleFilter.set(''); this.statusFilter.set(''); this.page.set(1); }
  isProtected(u: UserWithRoleDetails): boolean { return roleCode(u.role) === 'SUPER_ADMIN'; }

  constructor(private adminService: AdminService, authService: AuthService) {
    this.isSuperAdmin = authService.isSuperAdmin();
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.adminService.getUsers().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Erreur lors du chargement des utilisateurs.');
      }
    });
  }

  toggleUserActivation(user: UserWithRoleDetails): void {
    if (user.enabled) {
      this.adminService.deactivateUser(user.id).subscribe({
        next: () => {
          this.successMessage.set(`Compte de ${user.name} désactivé.`);
          this.loadUsers();
        },
        error: (err) => this.errorMessage.set(err.error?.message || "Échec de la désactivation du compte.")
      });
    } else {
      this.adminService.activateUser(user.id).subscribe({
        next: () => {
          this.successMessage.set(`Compte de ${user.name} activé avec succès.`);
          this.loadUsers();
        },
        error: (err) => this.errorMessage.set(err.error?.message || "Échec de l'activation du compte.")
      });
    }
  }

  openRoleModal(user: UserWithRoleDetails): void {
    this.selectedUser = user;
    this.selectedRole = user.role.replace('ROLE_', '');
    this.roleModalVisible.set(true);
  }

  saveRole(): void {
    if (!this.selectedUser || !this.selectedRole) return;

    this.adminService.assignRole(this.selectedUser.id, this.selectedRole).subscribe({
      next: () => {
        this.successMessage.set(`Rôle de ${this.selectedUser?.name} mis à jour en ${this.selectedRole}.`);
        this.roleModalVisible.set(false);
        this.loadUsers();
      },
      error: (err) => this.errorMessage.set(err.error?.message || "Échec de l'attribution du rôle.")
    });
  }

  deleteUser(user: UserWithRoleDetails): void {
    if (confirm(`Voulez-vous vraiment supprimer l'utilisateur ${user.name} ?`)) {
      this.adminService.deleteUser(user.id).subscribe({
        next: () => {
          this.successMessage.set(`Utilisateur ${user.name} supprimé.`);
          this.loadUsers();
        },
        error: (err) => this.errorMessage.set(err.error?.message || "Impossible de supprimer cet utilisateur.")
      });
    }
  }

  getRoleBadgeColor(role: string): string {
    const r = role?.toUpperCase() || '';
    if (r.includes('SUPER_ADMIN')) return 'dark';
    if (r.includes('ADMIN')) return 'danger';
    if (r.includes('TEACHER')) return 'warning';
    return 'info';
  }
}
