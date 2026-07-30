// ============================================================
// Fichier : src/app/pages/login/login.component.ts
// ============================================================

import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { AuthResponse, AuthService, LoginResponse } from '../../services/auth.service';
import { AuthenticatorPreference, WebauthnService } from '../../services/webauthn.service';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import gsap from 'gsap';

/** Étapes du parcours de connexion. */
type LoginStep = 'CREDENTIALS' | 'FACTOR_CHOICE' | 'WEBAUTHN_PENDING' | 'FACE_PENDING' | 'SUCCESS';

@Component({
  selector: 'app-login',
  standalone: false,
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
  animations: [
    trigger('fadeInOut', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('300ms ease-out', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('250ms ease-in', style({ opacity: 0 }))
      ])
    ]),
    trigger('slideDown', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-12px)' }),
        animate('400ms cubic-bezier(0.34, 1.56, 0.64, 1)', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ]),
    trigger('staggerIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(16px)' }),
        animate('500ms 200ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ]),
    // Les étapes se croisent latéralement plutôt que de se substituer d'un coup.
    trigger('stepSwap', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(24px)' }),
        animate('320ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ opacity: 1, transform: 'none' }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0, transform: 'translateX(-12px)' }))
      ])
    ])
  ]
})
export class LoginComponent implements OnInit, AfterViewInit, OnDestroy {

  private readonly minLoadingMs = 4000;
  private loadingTimer: ReturnType<typeof setTimeout> | null = null;

  matricule: string = '';
  password: string = '';
  errorMessage: string = '';
  successMessage: string = '';
  isLoading: boolean = false;

  // ─── Second facteur ───────────────────────────────────────────────────────
  step: LoginStep = 'CREDENTIALS';
  factors: string[] = [];
  mfaError = '';
  verifying = false;
  /** Appareil visé par la cérémonie en cours : change le texte de l'écran d'attente. */
  mfaDevice: AuthenticatorPreference = 'ANY';
  private mfaToken = '';

  /** Exposé au composant de capture faciale, qui l'envoie dans le corps JSON. */
  get mfaTokenPublic(): string {
    return this.mfaToken;
  }

  @ViewChild('formCard') formCardRef?: ElementRef<HTMLElement>;

  private ctx?: gsap.Context;
  private pulse?: gsap.core.Timeline;

  constructor(
    private authService: AuthService,
    private webauthn: WebauthnService,
    private router: Router
  ) { }

  ngOnInit(): void {
    // Si l'utilisateur est déjà connecté, ne pas effacer la session
    // Cela évite le problème où le composant se réinitialise après une connexion réussie
    // à cause du changement de router-outlet (authentifié vs non-authentifié)
    if (!this.authService.isAuthenticated()) {
      this.authService.clearSession();
    } else {
      this.navigateToDashboard();
    }
  }

  ngAfterViewInit(): void {
    this.ctx = gsap.context(() => {});
  }

  ngOnDestroy(): void {
    if (this.loadingTimer) {
      clearTimeout(this.loadingTimer);
    }
    this.pulse?.kill();
    this.ctx?.revert();
  }

  // ─── Étape 1 : mot de passe ───────────────────────────────────────────────

  onSubmit(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.matricule || !this.password) {
      this.errorMessage = 'Veuillez remplir tous les champs.';
      return;
    }

    this.isLoading = true;
    const startedAt = Date.now();

    this.authService.login({ matricule: this.matricule, motDePasse: this.password }).subscribe({
      next: (response: LoginResponse) => {
        if (response.mfaRequired) {
          // Aucun jeton posé : le shell applicatif ne bascule pas encore.
          this.mfaToken = response.mfaToken ?? '';
          this.factors = response.factors ?? ['WEBAUTHN'];
          this.isLoading = false;

          // Un seul facteur enrôlé : l'écran de choix serait une étape inutile.
          if (this.factors.length === 1) {
            this.chooseFactor(this.factors[0]);
          } else {
            this.goToStep('FACTOR_CHOICE');
          }
          return;
        }

        setTimeout(() => {
          this.isLoading = false;
          this.navigateToDashboard();
        }, 500);
      },
      error: (err: any) => {
        this.endLoading(startedAt, () => {
          this.errorMessage = this.messageErreur(err);
        });
      }
    });
  }

  // ─── Étape 2 : clé d'accès ────────────────────────────────────────────────

  chooseFactor(factor: string): void {
    if (factor === 'WEBAUTHN') {
      this.goToStep('WEBAUTHN_PENDING');
      this.startWebauthn('THIS_DEVICE');
    } else if (factor === 'PHONE') {
      this.goToStep('WEBAUTHN_PENDING');
      this.startWebauthn('PHONE');
    } else if (factor === 'FACE') {
      this.mfaError = '';
      this.goToStep('FACE_PENDING');
    }
  }

  /** Le composant de capture a obtenu la session : on peut poser les jetons. */
  onFaceAuthenticated(session: AuthResponse): void {
    this.goToStep('SUCCESS');
    // La coche masque la latence de la bascule du shell applicatif.
    setTimeout(() => {
      this.authService.completeSession(session);
      this.navigateToDashboard();
    }, 700);
  }

  onFaceExpired(): void {
    this.retourIdentifiants('Session de vérification expirée. Reconnectez-vous.');
  }

  /**
   * Lance la cérémonie. Avec THIS_DEVICE le navigateur ouvre Windows Hello ;
   * avec PHONE il affiche lui-même le QR code à scanner, que l'empreinte du
   * téléphone valide ensuite (transport hybride natif de WebAuthn).
   */
  async startWebauthn(appareil: AuthenticatorPreference = 'ANY'): Promise<void> {
    this.mfaDevice = appareil;
    this.mfaError = '';
    this.verifying = true;
    this.animatePulse();

    try {
      const options = await firstValueFrom(this.webauthn.loginOptions(this.mfaToken, appareil));
      const credential = await this.webauthn.promptAuthentication(options.optionsJSON);
      const result = await firstValueFrom(
        this.webauthn.loginVerify(this.mfaToken, options.ceremonyId, credential));

      if (!result?.session) {
        throw new Error('Réponse de vérification incomplète');
      }

      this.pulse?.kill();
      this.goToStep('SUCCESS');
      // La coche masque la latence de la bascule du shell applicatif.
      setTimeout(() => {
        this.authService.completeSession(result.session!);
        this.navigateToDashboard();
      }, 700);

    } catch (err: any) {
      this.verifying = false;
      this.pulse?.kill();

      if (err?.status === 401) {
        // Jeton d'étape expiré : on repart proprement du mot de passe.
        this.retourIdentifiants(err?.error?.message
          || 'Session de vérification expirée. Reconnectez-vous.');
        return;
      }
      this.mfaError = err?.error?.message || this.webauthn.describeError(err);
    }
  }

  /** Bascule PC ↔ téléphone sans repasser par le mot de passe. */
  basculerAppareil(): void {
    this.startWebauthn(this.mfaDevice === 'PHONE' ? 'THIS_DEVICE' : 'PHONE');
  }

  annuler(): void {
    this.retourIdentifiants('');
  }

  private retourIdentifiants(message: string): void {
    this.mfaToken = '';
    this.factors = [];
    this.mfaError = '';
    this.password = '';
    this.errorMessage = message;
    this.goToStep('CREDENTIALS');
  }

  // ─── Mouvement ────────────────────────────────────────────────────────────

  /** La carte s'ajuste en douceur : sans cela, chaque étape la fait sauter. */
  private goToStep(step: LoginStep): void {
    const card = this.formCardRef?.nativeElement;
    if (!card) {
      this.step = step;
      return;
    }

    const hauteurDepart = card.offsetHeight;
    this.step = step;

    requestAnimationFrame(() => {
      gsap.fromTo(card,
        { height: hauteurDepart },
        {
          height: 'auto',
          duration: 0.38,
          ease: 'power3.out',
          clearProps: 'height'
        });
    });
  }

  /** Anneaux concentriques pendant l'attente de l'empreinte. */
  private animatePulse(): void {
    this.pulse?.kill();
    requestAnimationFrame(() => {
      const anneaux = document.querySelectorAll('.mfa-ring');
      if (!anneaux.length) return;

      this.pulse = gsap.timeline({ repeat: -1 });
      anneaux.forEach((anneau, i) => {
        this.pulse!.fromTo(anneau,
          { scale: 1, opacity: 0.55 },
          { scale: 1.45, opacity: 0, duration: 1.8, ease: 'sine.out' },
          i * 0.6);
      });
    });
  }

  // ─── Utilitaires ──────────────────────────────────────────────────────────

  private messageErreur(err: any): string {
    if (err?.name === 'TimeoutError') {
      return 'Le serveur ne repond pas. Verifiez que le backend est demarre.';
    }
    if (err?.status === 0) {
      return 'Impossible de contacter le serveur. Verifiez le port API et CORS.';
    }
    if (err?.error?.message) {
      return err.error.message;
    }
    if (typeof err?.error === 'string') {
      return err.error;
    }
    return err?.message || 'Matricule ou mot de passe incorrect.';
  }

  private endLoading(startedAt: number, callback: () => void): void {
    const elapsed = Date.now() - startedAt;
    const remaining = Math.max(0, this.minLoadingMs - elapsed);

    this.loadingTimer = setTimeout(() => {
      this.isLoading = false;
      this.loadingTimer = null;
      callback();
    }, remaining);
  }

  private navigateToDashboard(): void {
    const role = this.authService.getRole();
    switch (role) {
      case 'ADMIN':
        this.router.navigate(['/dashboard/admin']);
        break;
      case 'N1':
        this.router.navigate(['/dashboard/n1']);
        break;
      case 'N2':
        this.router.navigate(['/dashboard/n2']);
        break;
      case 'EMPLOYE':
        this.router.navigate(['/dashboard/employe']);
        break;
      default:
        this.router.navigate(['/']);
    }
  }
}
