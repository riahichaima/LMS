import { PasswordToggleComponent } from '../../../shared/password-toggle.component';
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  AlertComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  ColComponent,
  ContainerComponent,
  FormControlDirective,
  InputGroupComponent,
  InputGroupTextDirective,
  RowComponent,
  SpinnerComponent
} from '@coreui/angular';
import { AuthService, PASSWORD_PATTERN, PASSWORD_RULE_MESSAGE } from '../../../services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [PasswordToggleComponent, 
    CommonModule,
    FormsModule,
    RouterLink,
    ContainerComponent,
    RowComponent,
    ColComponent,
    CardComponent,
    CardBodyComponent,
    InputGroupComponent,
    InputGroupTextDirective,
    IconDirective,
    FormControlDirective,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent
  ],
  templateUrl: './reset-password.component.html'
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  password = '';
  confirmPassword = '';
  loading = signal(false);
  errorMessage = signal('');
  done = signal(false);

  constructor(private authService: AuthService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.errorMessage.set('Lien de réinitialisation invalide. Refaites une demande.');
    }
  }

  onSubmit(): void {
    this.errorMessage.set('');

    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Les mots de passe ne correspondent pas.');
      return;
    }

    if (!PASSWORD_PATTERN.test(this.password)) {
      this.errorMessage.set(PASSWORD_RULE_MESSAGE);
      return;
    }

    this.loading.set(true);

    this.authService.resetPassword(this.token, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.done.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err.status === 0
          ? 'Connexion au serveur échouée. Vérifiez que le backend LMS est démarré.'
          : err.error?.message || 'La réinitialisation a échoué.');
      }
    });
  }
}
