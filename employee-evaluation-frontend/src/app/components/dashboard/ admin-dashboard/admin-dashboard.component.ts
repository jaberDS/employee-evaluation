import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  currentUser: any = { prenom: 'Jean', nom: 'Dupont', role: 'ADMIN' };
  today = new Date();
  currentYear = new Date().getFullYear();
  loading = false;

  stats = {
    totalEmployees: 12,
    activeEmployees: 10,
    totalEvaluations: 3,
    openEvaluations: 1,
    completedEvaluations: 8,
    pendingEvaluations: 2,
    averageNote: 7.5
  };

  constructor(private authService: AuthService) {}

  ngOnInit() {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUser = user;
      }
    });
  }
}
