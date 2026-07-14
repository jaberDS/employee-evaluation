import { AfterViewInit, Component, OnInit } from '@angular/core';
import { AuthService } from '../../../services/auth.service';

interface DisplayStats {
  totalEmployees: number;
  activeEmployees: number;
  totalEvaluations: number;
  openEvaluations: number;
  completedEvaluations: number;
  pendingEvaluations: number;
  averageNote: number;
}

interface RecentActivity {
  icon: 'user-plus' | 'megaphone' | 'check-circle' | 'clipboard-check';
  text: string;
  time: string;
  color: string;
}

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit, AfterViewInit {
  currentUser: any = { prenom: 'Jean', nom: 'Dupont', role: 'ADMIN' };
  today = new Date();
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

  displayStats: DisplayStats = {
    totalEmployees: 0,
    activeEmployees: 0,
    totalEvaluations: 0,
    openEvaluations: 0,
    completedEvaluations: 0,
    pendingEvaluations: 0,
    averageNote: 0
  };

  recentActivities: RecentActivity[] = [
    { icon: 'user-plus', text: 'Nouvel employé ajouté — Sarah Ben Ali', time: 'Il y a 2h', color: '#8B0000' },
    { icon: 'megaphone', text: 'Campagne Q3 2026 lancée', time: 'Il y a 5h', color: '#16A34A' },
    { icon: 'check-circle', text: '3 évaluations validées par N2', time: 'Hier', color: '#0284C7' },
    { icon: 'clipboard-check', text: 'Rapport mensuel généré', time: 'Il y a 2j', color: '#F59E0B' }
  ];

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUser = user;
      }
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.animateCounters(), 150);
  }

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Bonjour';
    if (hour < 18) return 'Bon après-midi';
    return 'Bonsoir';
  }

  get quickSummary(): string {
    return `${this.stats.activeEmployees} employés actifs · ${this.stats.openEvaluations} campagne ouverte · ${this.stats.pendingEvaluations} en attente`;
  }

  get employeeProgress(): number {
    if (!this.stats.totalEmployees) return 0;
    return Math.round((this.stats.activeEmployees / this.stats.totalEmployees) * 100);
  }

  get evaluationProgress(): number {
    const total = this.stats.completedEvaluations + this.stats.pendingEvaluations;
    if (!total) return 0;
    return Math.round((this.stats.completedEvaluations / total) * 100);
  }

  get campaignProgress(): number {
    if (!this.stats.totalEvaluations) return 0;
    return Math.round((this.stats.openEvaluations / this.stats.totalEvaluations) * 100);
  }

  get circularProgress(): number {
    return this.evaluationProgress;
  }

  get circularDashOffset(): number {
    const circumference = 2 * Math.PI * 42;
    return circumference - (this.evaluationProgress / 100) * circumference;
  }

  private animateCounters(): void {
    const duration = 1400;
    const start = performance.now();
    const targets = { ...this.stats };

    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);

      this.displayStats.totalEmployees = Math.round(targets.totalEmployees * eased);
      this.displayStats.activeEmployees = Math.round(targets.activeEmployees * eased);
      this.displayStats.totalEvaluations = Math.round(targets.totalEvaluations * eased);
      this.displayStats.openEvaluations = Math.round(targets.openEvaluations * eased);
      this.displayStats.completedEvaluations = Math.round(targets.completedEvaluations * eased);
      this.displayStats.pendingEvaluations = Math.round(targets.pendingEvaluations * eased);
      this.displayStats.averageNote = Math.round(targets.averageNote * eased * 10) / 10;

      if (progress < 1) {
        requestAnimationFrame(step);
      }
    };

    requestAnimationFrame(step);
  }
}
