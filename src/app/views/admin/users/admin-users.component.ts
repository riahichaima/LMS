import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  TableDirective,
  ButtonDirective,
  BadgeComponent,
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

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    TableDirective,
    ButtonDirective,
    BadgeComponent,
    SpinnerComponent,
    AlertComponent,
    FormSelectDirective,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    ModalFooterComponent,
    IconDirective
  ],
  templateUrl: './admin-users.component.html'
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
