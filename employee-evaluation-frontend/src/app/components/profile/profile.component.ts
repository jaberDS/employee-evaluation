import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/auth.model';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  currentUser: User | null = null;
  editMode = false;
  lastLogin = new Date(); // À remplacer par une vraie valeur
  showPasswordModal = false;
  
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;

  profileForm!: FormGroup;
  passwordForm!: FormGroup;

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
