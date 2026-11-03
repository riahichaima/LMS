import { Component, ElementRef, NgZone, OnDestroy, OnInit, inject, input, output, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthResponse, AuthService } from '../services/auth.service';

/** Erreur renvoyée au parent (message affiché, code pour réagir : GOOGLE_NO_ACCOUNT, ADMIN_PENDING…). */
export interface GoogleSigninError {
  code: string;
  message: string;
}

declare global {
  interface Window { google?: any; }
}

const GSI_SRC = 'https://accounts.google.com/gsi/client?hl=fr';
let gsiScript: Promise<void> | null = null;

/** Charge une seule fois le script officiel Google Identity Services. */
function loadGsi(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  gsiScript ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GSI_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => {
      gsiScript = null;
      reject(new Error('gsi'));
    };
    document.head.appendChild(script);
  });
  return gsiScript;
}

/**
 * Bouton officiel « Continuer avec Google ». Google renvoie un jeton d'identité signé que le backend vérifie,
 * puis connecte le compte (page de connexion) ou le crée avec le rôle choisi (page d'inscription).
 * Masqué si le serveur n'a pas de Client ID Google.
 */
@Component({
  selector: 'app-google-signin',
  standalone: true,
  template: `
    @if (enabled()) {
      <div class="gsi-divider"><span>ou</span></div>
      <div class="gsi-button" #button [class.gsi-busy]="busy()"></div>
      @if (busy()) { <p class="gsi-note">Connexion avec Google…</p> }
      @if (scriptError()) { <p class="gsi-note gsi-error">Le service Google est inaccessible (connexion Internet ou bloqueur de publicité ?).</p> }
    }
  `,
  styles: [`
    :host { display: block; }
    .gsi-divider { display: flex; align-items: center; gap: 12px; margin: 18px 0 14px; color: #6b7186; font-size: .85rem; }
    .gsi-divider::before, .gsi-divider::after { content: ''; flex: 1; height: 1px; background: #e6e9f2; }
    .gsi-button { display: flex; justify-content: center; min-height: 44px; }
    .gsi-busy { opacity: .5; pointer-events: none; }
    .gsi-note { margin: 8px 0 0; text-align: center; font-size: .82rem; color: #6b7186; }
    .gsi-error { color: #b42318; }
  `]
})
export class GoogleSigninComponent implements OnInit, OnDestroy {
  /** signin : page de connexion ; signup : page d'inscription (le rôle est envoyé). */
  mode = input<'signin' | 'signup'>('signin');
  role = input<string | null>(null);
  success = output<AuthResponse>();
  failure = output<GoogleSigninError>();

  readonly enabled = signal(false);
  readonly busy = signal(false);
  readonly scriptError = signal(false);

  private button = viewChild<ElementRef<HTMLElement>>('button');
  private auth = inject(AuthService);
  private zone = inject(NgZone);
  private destroyed = false;
  private resizeObserver?: ResizeObserver;

  async ngOnInit(): Promise<void> {
    let clientId = '';
    try {
      const config = await firstValueFrom(this.auth.googleConfig());
      if (!config.enabled || !config.clientId) return;
      clientId = config.clientId;
    } catch {
      return; // backend ancien ou injoignable : la connexion classique reste disponible
    }
    this.enabled.set(true);
    try {
      await loadGsi();
    } catch {
      this.scriptError.set(true);
      return;
    }
    if (this.destroyed) return;
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (res: { credential: string }) => this.zone.run(() => this.onCredential(res.credential)),
      ux_mode: 'popup',
      context: this.mode() === 'signup' ? 'signup' : 'signin',
      itp_support: true
    });
    // Le conteneur n'existe qu'après l'affichage du bloc @if
    setTimeout(() => this.render());
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.resizeObserver?.disconnect();
  }

  private render(): void {
    const el = this.button()?.nativeElement;
    if (!el || this.destroyed) return;
    const draw = () => {
      el.innerHTML = '';
      window.google.accounts.id.renderButton(el, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        shape: 'pill',
        text: this.mode() === 'signup' ? 'signup_with' : 'continue_with',
        logo_alignment: 'center',
        locale: 'fr_FR',
        // Largeur du formulaire (Google accepte 200 à 400 px)
        width: Math.max(200, Math.min(400, Math.floor(el.clientWidth || 320)))
      });
    };
    draw();
    let lastWidth = el.clientWidth;
    this.resizeObserver = new ResizeObserver(() => {
      if (Math.abs(el.clientWidth - lastWidth) > 24) {
        lastWidth = el.clientWidth;
        draw();
      }
    });
    this.resizeObserver.observe(el);
  }

  private onCredential(credential: string): void {
    if (!credential || this.busy()) return;
    this.busy.set(true);
    const role = this.mode() === 'signup' ? this.role() ?? 'STUDENT' : undefined;
    this.auth.loginWithGoogle(credential, role).subscribe({
      next: res => {
        this.busy.set(false);
        this.success.emit(res);
      },
      error: err => {
        this.busy.set(false);
        this.failure.emit({
          code: err.error?.code ?? (err.status === 0 ? 'NETWORK' : 'ERROR'),
          message: err.status === 0
            ? 'Connexion au serveur échouée. Vérifiez que le backend LMS est démarré.'
            : err.error?.message || 'La connexion avec Google a échoué.'
        });
      }
    });
  }
}
