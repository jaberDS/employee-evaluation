import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
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

  profileForm!: FormGroup;
  passwordForm!: FormGroup;

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
      newPassword: ['', [Validators.required, Validators.minLength(4)]],
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

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.toastr.warning('Veuillez remplir tous les champs', 'Attention');
      return;
    }
    if (this.passwordForm.hasError('mismatch')) {
      this.toastr.error('Les mots de passe ne correspondent pas', 'Erreur');
      return;
    }
    // Appel API pour changer le mot de passe
    this.toastr.success('Mot de passe modifié avec succès', 'Succès');
    this.passwordForm.reset();
    // Fermer le modal
    const modal = document.getElementById('changePasswordModal');
    if (modal) {
      const btn = modal.querySelector('[data-bs-dismiss="modal"]') as HTMLElement;
      if (btn) btn.click();
    }
  }
}
