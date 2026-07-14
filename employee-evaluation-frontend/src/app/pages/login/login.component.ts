// ============================================================
// Fichier : src/app/features/login/login.component.ts
// ============================================================

import { Component, OnDestroy, OnInit } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { AuthService, AuthResponse } from '../../services/auth.service';
import { Router } from '@angular/router';

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
    ])
  ]
})
export class LoginComponent implements OnInit, OnDestroy {

  private readonly minLoadingMs = 4000;
  private loadingTimer: ReturnType<typeof setTimeout> | null = null;

  // Modèles liés aux champs du formulaire (two-way binding)
  matricule: string = '';
  password: string = '';
  errorMessage: string = '';
  successMessage: string = '';
  isLoading: boolean = false;

  // Injection des dépendances
  constructor(
    private authService: AuthService,
    private router: Router
  ) { }

  ngOnInit(): void {
    // Si l'utilisateur est déjà connecté, ne pas effacer la session
    // Cela évite le problème où le composant se réinitialise après une connexion réussie
    // à cause du changement de router-outlet (authentifié vs non-authentifié)
    if (!this.authService.isAuthenticated()) {
      this.authService.clearSession();
    } else {
      // Rediriger vers le tableau de bord approprié si déjà connecté
      this.navigateToDashboard();
    }
  }

  ngOnDestroy(): void {
    if (this.loadingTimer) {
      clearTimeout(this.loadingTimer);
    }
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
        this.router.navigate(['/dashboard/employee']);
        break;
      default:
        this.router.navigate(['/']);
    }
  }

  // Méthode appelée lors de la soumission du formulaire
  onSubmit(): void {
    // Réinitialiser les messages d'erreur
    this.errorMessage = '';
    this.successMessage = '';

    // Vérifier que les champs ne sont pas vides
    if (!this.matricule || !this.password) {
      this.errorMessage = 'Veuillez remplir tous les champs.';
      return;
    }

    // Activer l'indicateur de chargement
    this.isLoading = true;
    const startedAt = Date.now();
    console.log('📤 Tentative de connexion pour matricule :', this.matricule);

    // Appel au service d'authentification
    this.authService.login({ matricule: this.matricule, motDePasse: this.password }).subscribe({
      next: (response: AuthResponse) => {
        console.log('✅ Connexion réussie !', response);

        // Utiliser setTimeout pour s'assurer que le DOM se met à jour
        // avant de naviguer vers le tableau de bord
        setTimeout(() => {
          this.isLoading = false;
          this.navigateToDashboard();
        }, 500);
      },
      error: (err: any) => {
        console.error('❌ Échec de la connexion :', err);
        this.endLoading(startedAt, () => {
          if (err?.name === 'TimeoutError') {
            this.errorMessage = 'Le serveur ne repond pas. Verifiez que le backend est demarre.';
            return;
          }

          if (err?.status === 0) {
            this.errorMessage = 'Impossible de contacter le serveur. Verifiez le port API et CORS.';
            return;
          }

          // Afficher le message d'erreur du backend s'il est disponible
          if (err?.error?.message) {
            this.errorMessage = err.error.message;
          } else if (typeof err?.error === 'string') {
            this.errorMessage = err.error;
          } else if (err?.message) {
            this.errorMessage = err.message;
          } else {
            this.errorMessage = 'Matricule ou mot de passe incorrect.';
          }
        });
      }
    });
  }
}