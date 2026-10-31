import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  InputGroupComponent,
  InputGroupTextDirective,
  FormControlDirective,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent
} from '@coreui/angular';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    IconDirective,
    InputGroupComponent,
    InputGroupTextDirective,
    FormControlDirective,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent
  ],
  templateUrl: './login.component.html',
  styleUrls: ['../auth-pages.scss']
})
export class LoginComponent {
  email = '';
  password = '';
  loading = signal(false);
  errorMessage = signal('');
  // Compte créé mais email pas encore vérifié : propose de renvoyer le lien
  notVerified = signal(false);
  resending = signal(false);
  infoMessage = signal('');

  constructor(private authService: AuthService, private router: Router, private route: ActivatedRoute) {}

  onLogin(): void {
    if (!this.email || !this.password) {
      this.errorMessage.set('Veuillez saisir votre email et mot de passe.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.notVerified.set(false);

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.router.navigateByUrl(returnUrl || this.authService.getHomeRoute());
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 0) {
          this.errorMessage.set('Connexion au serveur échouée. Vérifiez que le backend LMS est démarré.');
        } else {
          this.notVerified.set(err.error?.code === 'EMAIL_NOT_VERIFIED');
          this.errorMessage.set(err.error?.message || 'Identifiants incorrects.');
        }
      }
    });
  }

  resendVerification(): void {
    this.resending.set(true);
    this.authService.resendVerification(this.email).subscribe({
      next: (res) => {
        this.resending.set(false);
        this.notVerified.set(false);
        this.errorMessage.set('');
        this.infoMessage.set(res.message);
      },
      error: () => {
        this.resending.set(false);
        this.errorMessage.set("L'envoi a échoué, réessayez dans un instant.");
      }
    });
  }
}
