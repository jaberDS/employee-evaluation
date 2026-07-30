import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { firstValueFrom } from 'rxjs';
import gsap from 'gsap';
import { AuthService } from '../../services/auth.service';
import { WebauthnService, PasskeySummary, AuthenticatorPreference } from '../../services/webauthn.service';
import { FaceService } from '../../services/face.service';
import { User } from '../../models/auth.model';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit, AfterViewInit {
  currentUser: User | null = null;
  editMode = false;
  lastLogin = new Date(); // À remplacer par une vraie valeur
  showPasswordModal = false;

  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;

  profileForm!: FormGroup;
  passwordForm!: FormGroup;

  @ViewChild('secPanel') secPanel?: ElementRef<HTMLElement>;

  // ─── Clés d'accès (passkeys) ──────────────────────────────────────────────
  passkeys: PasskeySummary[] = [];
  passkeysLoading = true;
  showPasskeyModal = false;
  passkeyLabel = '';
  passkeyBusy = false;
  passkeyError = '';
  passkeySupported = true;
  /** Appareil visé par l'enrôlement : ce PC, ou le téléphone via QR code. */
  passkeyDevice: AuthenticatorPreference = 'THIS_DEVICE';

  showDeletePasskeyModal = false;
  deletePasskeyTarget: PasskeySummary | null = null;
  deletePasskeyPassword = '';
  deletePasskeyError = '';

  // ─── Reconnaissance faciale ───────────────────────────────────────────────
  hasFace = false;
  faceSupported = true;
  showFaceModal = false;
  showDeleteFaceModal = false;
  deleteFacePassword = '';
  deleteFaceError = '';
  faceBusy = false;

  /** Circonférence du cercle r=34 de l'anneau de robustesse. */
  readonly scoreCircumference = 2 * Math.PI * 34;
  /** Valeur affichée, animée par GSAP depuis 0. */
  displayScore = 0;

  // Password strength criteria checks
  hasMinLength = false;
  hasUppercase = false;
  hasLowercase = false;
  hasNumber = false;
  hasSpecialChar = false;

  stats = {
    totalEvaluations: 0,
    averageNote: 0,
    pendingEvaluations: 0
  };

  constructor(
    private authService: AuthService,
    private webauthn: WebauthnService,
    private face: FaceService,
    private fb: FormBuilder,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.initForms();

    this.authService.currentUser$.subscribe({
      next: (user) => {
        this.currentUser = user;
        if (user) {
          this.profileForm.patchValue({
            matricule: user.matricule,
            nom: user.nom,
            prenom: user.prenom,
            email: user.email,
            role: user.role,
            departement: 'Informatique' // À remplacer par une vraie valeur
          });
        }
      }
    });

    // Simuler des stats (à remplacer par un vrai appel API)
    this.stats = {
      totalEvaluations: 12,
      averageNote: 7.8,
      pendingEvaluations: 2
    };

    // Watch new password changes for strength indicator
    this.passwordForm.get('newPassword')?.valueChanges.subscribe(value => {
      this.checkPasswordStrength(value || '');
    });

    this.passkeySupported = this.webauthn.isSupported();
    this.faceSupported = this.face.isSupported();
    this.loadPasskeys();
    this.loadFaceStatus();
  }

  ngAfterViewInit(): void {
    this.animateSecurityPanel();
  }

  // ═══ Panneau sécurité ════════════════════════════════════════════════════

  /** Le compte est-il couvert par un second facteur ? */
  get hasMfa(): boolean {
    return this.passkeys.length > 0 || this.hasFace;
  }

  /**
   * Score de robustesse : le mot de passe compte pour 40, chaque facteur
   * biométrique pour 30. Trois facteurs → 100.
   */
  private get securityScore(): number {
    return 40 + (this.passkeys.length ? 30 : 0) + (this.hasFace ? 30 : 0);
  }

  /** Décalage du tracé de l'anneau, dérivé du score affiché. */
  get scoreOffset(): number {
    return this.scoreCircumference * (1 - this.displayScore / 100);
  }

  /** Entrée en cascade du panneau, puis comptage de l'anneau. */
  private animateSecurityPanel(): void {
    const panel = this.secPanel?.nativeElement;
    if (!panel || this.prefersReducedMotion()) {
      this.displayScore = this.securityScore;
      return;
    }

    const timeline = gsap.timeline({ defaults: { ease: 'power3.out' } });

    timeline.from(panel.querySelector('.sec-hero'), { y: 24, opacity: 0, duration: 0.55 });

    const cartes = panel.querySelectorAll('.sec-factor');
    if (cartes.length) {
      timeline.from(cartes, { y: 22, opacity: 0, duration: 0.5, stagger: 0.09 }, '-=0.3');
    }

    timeline.from(panel.querySelector('.sec-devices'), { y: 18, opacity: 0, duration: 0.45 }, '-=0.25');

    this.animateScore();
  }

  /** Le chiffre grimpe en même temps que l'anneau se remplit. */
  private animateScore(): void {
    const cible = this.securityScore;
    if (this.prefersReducedMotion()) {
      this.displayScore = cible;
      return;
    }
    const compteur = { valeur: this.displayScore };
    gsap.to(compteur, {
      valeur: cible,
      duration: 1.1,
      delay: 0.25,
      ease: 'power2.out',
      onUpdate: () => { this.displayScore = Math.round(compteur.valeur); }
    });
  }

  private prefersReducedMotion(): boolean {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  // ═══ Clés d'accès ═══════════════════════════════════════════════════════

  loadPasskeys(): void {
    this.passkeysLoading = true;
    this.webauthn.listPasskeys().subscribe({
      next: (keys) => {
        this.passkeys = keys;
        this.passkeysLoading = false;
        this.animateDevices();
        this.animateScore();
      },
      error: () => {
        this.passkeys = [];
        this.passkeysLoading = false;
      }
    });
  }

  private animateDevices(): void {
    if (this.prefersReducedMotion()) return;
    requestAnimationFrame(() => {
      const lignes = this.secPanel?.nativeElement.querySelectorAll('.sec-device');
      if (!lignes?.length) return;
      gsap.from(lignes, {
        x: -14, opacity: 0, duration: 0.42, stagger: 0.07, ease: 'power2.out'
      });
    });
  }

  openPasskeyModal(): void {
    this.passkeyDevice = 'THIS_DEVICE';
    this.passkeyLabel = this.defaultDeviceLabel();
    this.passkeyError = '';
    this.showPasskeyModal = true;
  }

  closePasskeyModal(): void {
    if (this.passkeyBusy) return;
    this.showPasskeyModal = false;
  }

  /**
   * Enrôle une clé.
   *
   * Le hint envoyé au serveur oriente l'invite : « cet ordinateur » ouvre
   * Windows Hello, « mon téléphone » fait afficher le QR code par le navigateur
   * lui-même — c'est le transport hybride natif de WebAuthn, aucun canal QR
   * n'est à construire de notre côté.
   */
  async enrollPasskey(): Promise<void> {
    if (!this.passkeyLabel.trim()) {
      this.passkeyError = 'Donnez un nom à cet appareil.';
      return;
    }

    this.passkeyError = '';
    this.passkeyBusy = true;

    try {
      const options = await firstValueFrom(this.webauthn.registerOptions(this.passkeyDevice));
      const credential = await this.webauthn.promptRegistration(options.optionsJSON);
      await firstValueFrom(this.webauthn.registerVerify(
        options.ceremonyId, this.passkeyLabel.trim(), credential));

      this.passkeyBusy = false;
      this.showPasskeyModal = false;
      this.toastr.success('Clé d\'accès enregistrée. Elle sera demandée à la prochaine connexion.', 'Succès');
      this.loadPasskeys();

    } catch (err: any) {
      this.passkeyBusy = false;
      this.passkeyError = err?.error?.message || this.webauthn.describeError(err);
    }
  }

  askDeletePasskey(passkey: PasskeySummary): void {
    this.deletePasskeyTarget = passkey;
    this.deletePasskeyPassword = '';
    this.deletePasskeyError = '';
    this.showDeletePasskeyModal = true;
  }

  closeDeletePasskeyModal(): void {
    if (this.passkeyBusy) return;
    this.showDeletePasskeyModal = false;
    this.deletePasskeyTarget = null;
  }

  confirmDeletePasskey(): void {
    if (!this.deletePasskeyTarget) return;
    if (!this.deletePasskeyPassword) {
      this.deletePasskeyError = 'Saisissez votre mot de passe actuel.';
      return;
    }

    this.passkeyBusy = true;
    this.webauthn.deletePasskey(this.deletePasskeyTarget.id, this.deletePasskeyPassword).subscribe({
      next: () => {
        this.passkeyBusy = false;
        this.showDeletePasskeyModal = false;
        this.deletePasskeyTarget = null;
        this.toastr.success('Clé d\'accès supprimée', 'Succès');
        this.loadPasskeys();
      },
      error: (err) => {
        this.passkeyBusy = false;
        this.deletePasskeyError = err?.error?.message || 'La suppression a échoué';
      }
    });
  }

  /** Nom pré-rempli déduit du système, que l'utilisateur peut corriger. */
  private defaultDeviceLabel(): string {
    const ua = navigator.userAgent;
    if (/iPhone|iPad/.test(ua)) return 'Mon iPhone';
    if (/Android/.test(ua)) return 'Mon téléphone Android';
    if (/Mac/.test(ua)) return 'Mon Mac';
    if (/Windows/.test(ua)) return 'Mon PC Windows';
    return 'Mon appareil';
  }

  // ═══ Reconnaissance faciale ══════════════════════════════════════════════

  private loadFaceStatus(): void {
    this.face.status().subscribe({
      next: (etat) => {
        this.hasFace = etat.enrolled;
        this.animateScore();
      },
      error: () => { this.hasFace = false; }
    });
  }

  openFaceModal(): void {
    this.showFaceModal = true;
  }

  /** Rappelé par le composant de capture une fois l'inscription réussie. */
  onFaceEnrolled(): void {
    this.showFaceModal = false;
    this.hasFace = true;
    this.animateScore();
    this.toastr.success('Reconnaissance faciale activée', 'Succès');
  }

  closeFaceModal(): void {
    this.showFaceModal = false;
  }

  askDeleteFace(): void {
    this.deleteFacePassword = '';
    this.deleteFaceError = '';
    this.showDeleteFaceModal = true;
  }

  closeDeleteFaceModal(): void {
    if (this.faceBusy) return;
    this.showDeleteFaceModal = false;
  }

  confirmDeleteFace(): void {
    if (!this.deleteFacePassword) {
      this.deleteFaceError = 'Saisissez votre mot de passe actuel.';
      return;
    }
    this.faceBusy = true;
    this.face.remove(this.deleteFacePassword).subscribe({
      next: () => {
        this.faceBusy = false;
        this.showDeleteFaceModal = false;
        this.hasFace = false;
        this.animateScore();
        this.toastr.success('Reconnaissance faciale désactivée', 'Succès');
      },
      error: (err) => {
        this.faceBusy = false;
        this.deleteFaceError = err?.error?.message || 'La suppression a échoué';
      }
    });
  }

  checkPasswordStrength(password: string): void {
    this.hasMinLength = password.length >= 8;
    this.hasUppercase = /[A-Z]/.test(password);
    this.hasLowercase = /[a-z]/.test(password);
    this.hasNumber = /[0-9]/.test(password);
    this.hasSpecialChar = /[^a-zA-Z0-9]/.test(password);
  }

  strongPasswordValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value || '';
    const hasMinLength = value.length >= 8;
    const hasUppercase = /[A-Z]/.test(value);
    const hasLowercase = /[a-z]/.test(value);
    const hasNumber = /[0-9]/.test(value);
    const hasSpecialChar = /[^a-zA-Z0-9]/.test(value);

    if (!hasMinLength || !hasUppercase || !hasLowercase || !hasNumber || !hasSpecialChar) {
      return { weakPassword: true };
    }
    return null;
  }

  initForms(): void {
    this.profileForm = this.fb.group({
      matricule: [{ value: '', disabled: true }],
      nom: ['', Validators.required],
      prenom: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      role: [{ value: '', disabled: true }],
      departement: ['']
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, this.strongPasswordValidator.bind(this)]],
      confirmPassword: ['', Validators.required]
    }, { validator: this.passwordMatchValidator });
  }

  passwordMatchValidator(form: FormGroup): any {
    const newPw = form.get('newPassword')?.value;
    const confirmPw = form.get('confirmPassword')?.value;
    return newPw === confirmPw ? null : { mismatch: true };
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.toastr.warning('Veuillez corriger les erreurs', 'Attention');
      return;
    }
    const data = this.profileForm.getRawValue();
    // Appel API pour mettre à jour
    this.toastr.success('Profil mis à jour avec succès', 'Succès');
    this.editMode = false;
  }

  openPasswordModal(): void {
    this.showPasswordModal = true;
  }

  closePasswordModal(): void {
    this.showPasswordModal = false;
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.toastr.warning('Veuillez corriger les erreurs', 'Attention');
      return;
    }
    if (this.passwordForm.hasError('mismatch')) {
      this.toastr.error('Les mots de passe ne correspondent pas', 'Erreur');
      return;
    }

    const currentPassword = this.passwordForm.get('currentPassword')?.value;
    const newPassword = this.passwordForm.get('newPassword')?.value;

    this.authService.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.toastr.success('Mot de passe modifié avec succès', 'Succès');
        this.passwordForm.reset();
        // Reset strength indicators
        this.hasMinLength = false;
        this.hasUppercase = false;
        this.hasLowercase = false;
        this.hasNumber = false;
        this.hasSpecialChar = false;
        // Close modal
        this.closePasswordModal();
      },
      error: (err) => {
        const errorMsg = err?.error?.message || 'Erreur lors du changement de mot de passe';
        this.toastr.error(errorMsg, 'Erreur');
      }
    });
  }
}
