import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import gsap from 'gsap';
import { WebauthnService } from '../../services/webauthn.service';
import { FaceService } from '../../services/face.service';

type RecoveryStep = 'IDENTIFY' | 'METHOD' | 'PASSKEY' | 'FACE' | 'NEW_PASSWORD' | 'DONE';

/**
 * Récupération de compte par second facteur.
 *
 * L'application n'a pas d'infrastructure de courriel : la preuve de possession
 * d'un passkey — ou d'un visage vivant — avec vérification biométrique
 * obligatoire remplace le lien de réinitialisation, et constitue une preuve
 * plus forte.
 */
@Component({
  selector: 'app-forgot-password',
  standalone: false,
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.css'],
  animations: [
    trigger('stepSwap', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(24px)' }),
        animate('320ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'none' }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0, transform: 'translateX(-12px)' }))
      ])
    ]),
    trigger('slideDown', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-12px)' }),
        animate('380ms cubic-bezier(0.34, 1.56, 0.64, 1)',
          style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ])
  ]
})
export class ForgotPasswordComponent implements OnDestroy {

  step: RecoveryStep = 'IDENTIFY';

  matricule = '';
  newPassword = '';
  confirmPassword = '';
  showPassword = false;

  errorMessage = '';
  busy = false;

  private mfaToken = '';
  private resetToken = '';

  /** Exposé au composant de capture faciale, qui l'envoie dans le corps JSON. */
  get mfaTokenPublic(): string {
    return this.mfaToken;
  }

  @ViewChild('formCard') formCardRef?: ElementRef<HTMLElement>;
  private pulse?: gsap.core.Timeline;

  constructor(
    private webauthn: WebauthnService,
    private face: FaceService,
    private router: Router
  ) {}

  get faceSupported(): boolean {
    return this.face.isSupported();
  }

  get passkeySupported(): boolean {
    return this.webauthn.isSupported();
  }

  ngOnDestroy(): void {
    this.pulse?.kill();
  }

  // ─── Critères de robustesse ───────────────────────────────────────────────

  get hasMinLength(): boolean { return this.newPassword.length >= 8; }
  get hasUppercase(): boolean { return /[A-Z]/.test(this.newPassword); }
  get hasLowercase(): boolean { return /[a-z]/.test(this.newPassword); }
  get hasNumber(): boolean { return /\d/.test(this.newPassword); }
  get hasSpecialChar(): boolean { return /[^a-zA-Z0-9]/.test(this.newPassword); }

  get passwordValid(): boolean {
    return this.hasMinLength && this.hasUppercase && this.hasLowercase
        && this.hasNumber && this.hasSpecialChar;
  }

  get passwordsMatch(): boolean {
    return this.newPassword.length > 0 && this.newPassword === this.confirmPassword;
  }

  get strength(): 'weak' | 'medium' | 'strong' {
    const remplis = [this.hasMinLength, this.hasUppercase, this.hasLowercase,
                     this.hasNumber, this.hasSpecialChar].filter(Boolean).length;
    if (remplis <= 2) return 'weak';
    if (remplis <= 4) return 'medium';
    return 'strong';
  }

  // ─── Étape 1 — identification ─────────────────────────────────────────────

  async identifier(): Promise<void> {
    this.errorMessage = '';
    if (!this.matricule.trim()) {
      this.errorMessage = 'Veuillez saisir votre matricule.';
      return;
    }
    if (!this.passkeySupported && !this.faceSupported) {
      this.errorMessage = 'Ce navigateur ne prend en charge aucune méthode de récupération.';
      return;
    }

    this.busy = true;
    try {
      const response = await firstValueFrom(this.webauthn.recoveryStart(this.matricule.trim()));
      this.mfaToken = response.mfaToken ?? '';
      this.busy = false;

      // Le serveur annonce les mêmes facteurs pour tous les comptes (anti-
      // énumération) : c'est donc au navigateur de filtrer ce qu'il sait faire.
      if (!this.faceSupported) {
        this.choisirMethode('WEBAUTHN');
      } else if (!this.passkeySupported) {
        this.choisirMethode('FACE');
      } else {
        this.goToStep('METHOD');
      }

    } catch (err: any) {
      this.busy = false;
      this.errorMessage = err?.error?.message
        || 'Impossible de démarrer la récupération. Réessayez.';
    }
  }

  choisirMethode(methode: 'WEBAUTHN' | 'FACE'): void {
    this.errorMessage = '';
    if (methode === 'WEBAUTHN') {
      this.goToStep('PASSKEY');
      this.verifierCle();
    } else {
      this.goToStep('FACE');
    }
  }

  /** Le composant de capture a obtenu le jeton de réinitialisation. */
  onFaceRecovered(resetToken: string): void {
    this.resetToken = resetToken;
    this.goToStep('NEW_PASSWORD');
  }

  onFaceExpired(): void {
    this.errorMessage = 'Session de vérification expirée. Recommencez.';
    this.recommencer();
  }

  // ─── Étape 2 — preuve par clé d'accès ─────────────────────────────────────

  async verifierCle(): Promise<void> {
    this.errorMessage = '';
    this.busy = true;
    this.animatePulse();

    try {
      const options = await firstValueFrom(this.webauthn.recoveryOptions(this.mfaToken));
      const credential = await this.webauthn.promptAuthentication(options.optionsJSON);
      const result = await firstValueFrom(
        this.webauthn.recoveryVerify(this.mfaToken, options.ceremonyId, credential));

      this.resetToken = result.resetToken;
      this.busy = false;
      this.pulse?.kill();
      this.goToStep('NEW_PASSWORD');

    } catch (err: any) {
      this.busy = false;
      this.pulse?.kill();
      // Volontairement identique quel que soit le motif réel : un message
      // distinct révélerait si le matricule existe et possède une clé.
      this.errorMessage = err?.status === 401
        ? 'Aucune clé d\'accès valide pour ce compte sur cet appareil.'
        : (err?.error?.message || this.webauthn.describeError(err));
    }
  }

  // ─── Étape 3 — nouveau mot de passe ───────────────────────────────────────

  async definirMotDePasse(): Promise<void> {
    this.errorMessage = '';
    if (!this.passwordValid) {
      this.errorMessage = 'Le mot de passe ne respecte pas les critères de sécurité.';
      return;
    }
    if (!this.passwordsMatch) {
      this.errorMessage = 'Les deux mots de passe ne correspondent pas.';
      return;
    }

    this.busy = true;
    try {
      await firstValueFrom(this.webauthn.resetPassword(this.resetToken, this.newPassword));
      this.busy = false;
      this.goToStep('DONE');
    } catch (err: any) {
      this.busy = false;
      this.errorMessage = err?.error?.message
        || 'La réinitialisation a échoué. Recommencez la procédure.';
    }
  }

  retourConnexion(): void {
    this.router.navigate(['/login']);
  }

  recommencer(): void {
    this.mfaToken = '';
    this.resetToken = '';
    this.errorMessage = '';
    this.goToStep('IDENTIFY');
  }

  // ─── Mouvement ────────────────────────────────────────────────────────────

  private goToStep(step: RecoveryStep): void {
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
        { height: 'auto', duration: 0.38, ease: 'power3.out', clearProps: 'height' });
    });
  }

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
}
