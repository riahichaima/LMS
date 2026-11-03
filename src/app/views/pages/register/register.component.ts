import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  InputGroupComponent,
  InputGroupTextDirective,
  FormControlDirective,
  FormSelectDirective,
  SpinnerComponent,
  AlertComponent
} from '@coreui/angular';
import { AuthService, PASSWORD_PATTERN, PASSWORD_RULE_MESSAGE } from '../../../services/auth.service';
// À RÉACTIVER APRÈS LE DÉPLOIEMENT (connexion Google) : remettre GoogleSigninComponent dans imports: [...] ci-dessous
import { GoogleSigninError } from '../../../shared/google-signin.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    IconDirective,
    InputGroupComponent,
    InputGroupTextDirective,
    FormControlDirective,
    FormSelectDirective,
    SpinnerComponent,
    AlertComponent
    // GoogleSigninComponent
  ],
  templateUrl: './register.component.html',
  styleUrls: ['../auth-pages.scss']
})
export class RegisterComponent {
  name = '';
  email = '';
  password = '';
  confirmPassword = '';
  role = signal('STUDENT');
  loading = signal(false);
  errorMessage = signal('');
  // Email du compte créé : affiche l'écran "vérifiez votre boîte mail"
  registeredEmail = signal('');
  registeredRole = signal('');
  resending = signal(false);
  resendMessage = signal('');
  // Inscription Google d'un administrateur : demande envoyée au super administrateur
  googleInfo = signal('');

  constructor(private authService: AuthService, private router: Router) {}

  /** Compte Google créé (ou déjà existant) : connecté directement, pas d'email de vérification à attendre. */
  onGoogle(): void {
    this.router.navigateByUrl(this.authService.getHomeRoute());
  }

  onGoogleError(error: GoogleSigninError): void {
    if (error.code === 'ADMIN_PENDING') {
      this.errorMessage.set('');
      this.googleInfo.set(error.message);
    } else {
      this.googleInfo.set('');
      this.errorMessage.set(error.message);
    }
  }

  onRegister(): void {
    this.errorMessage.set('');

    if (!this.name || !this.email || !this.password) {
      this.errorMessage.set('Veuillez remplir tous les champs obligatoires.');
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Les mots de passe ne correspondent pas.');
      return;
    }

    if (!PASSWORD_PATTERN.test(this.password)) {
      this.errorMessage.set(PASSWORD_RULE_MESSAGE);
      return;
    }

    this.loading.set(true);

    this.authService.register({
      name: this.name,
      email: this.email,
      password: this.password,
      role: this.role()
    }).subscribe({
      next: () => {
        this.loading.set(false);
        this.registeredRole.set(this.role());
        this.registeredEmail.set(this.email);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 0) {
          this.errorMessage.set('Connexion au serveur échouée. Vérifiez que le backend LMS est démarré.');
        } else {
          this.errorMessage.set(err.error?.message || "L'inscription a échoué. Vérifiez les données saisies.");
        }
      }
    });
  }

  resendVerification(): void {
    this.resending.set(true);
    this.resendMessage.set('');
    this.authService.resendVerification(this.registeredEmail()).subscribe({
      next: (res) => {
        this.resending.set(false);
        this.resendMessage.set(res.message);
      },
      error: () => {
        this.resending.set(false);
        this.resendMessage.set("L'envoi a échoué, réessayez dans un instant.");
      }
    });
  }
}
