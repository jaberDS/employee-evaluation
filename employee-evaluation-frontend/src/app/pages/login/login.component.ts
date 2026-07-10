import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  isLoading = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private toastr: ToastrService
  ) {
    this.loginForm = this.fb.group({
      matricule: ['', [Validators.required]],
      motDePasse: ['', [Validators.required, Validators.minLength(4)]]
    });
  }

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      const role = this.authService.getRole();
      this.router.navigate([this.getDashboard(role || 'EMPLOYE')]);
    }
  }

  get f() { return this.loginForm.controls; }

  onSubmit(): void {
    this.errorMessage = '';
    if (this.loginForm.invalid) {
      this.errorMessage = 'Veuillez remplir tous les champs.';
      return;
    }

    this.isLoading = true;
    this.authService.login(this.loginForm.value).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.toastr.success(`Bienvenue ${response.prenom} ${response.nom}`, 'Connexion réussie');
        this.router.navigate([this.getDashboard(response.role)]);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Matricule ou mot de passe incorrect.';
        this.toastr.error(this.errorMessage, 'Erreur de connexion');
      }
    });
  }

  private getDashboard(role: string): string {
    const dashboards: { [key: string]: string } = {
      'ADMIN': '/dashboard/admin',
      'N1': '/dashboard/n1',
      'N2': '/dashboard/n2',
      'EMPLOYE': '/dashboard/employee'
    };
    return dashboards[role] || '/dashboard';
  }
}
