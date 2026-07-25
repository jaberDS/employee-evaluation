import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { DashboardService, DashboardStats } from '../../../services/dashboard.service';
import { ActiviteService } from '../../../services/activite.service';
import { ActivityView, toActivityView } from '../../../shared/activity-view';

interface DisplayStats {
  totalEmployees: number;
  activeEmployees: number;
  totalEvaluations: number;
  openEvaluations: number;
  completedEvaluations: number;
  pendingEvaluations: number;
  averageNote: number;
}

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('activitySection') activitySection!: ElementRef;

  currentUser: any = null;
  today = new Date();
  loading = true;
  highlightedActivityId: number | null = null;
  private activitiesSub?: Subscription;
  private routeSub?: Subscription;

  stats = {
    totalEmployees: 0,
    activeEmployees: 0,
    totalEvaluations: 0,
    openEvaluations: 0,
    completedEvaluations: 0,
    pendingEvaluations: 0,
    averageNote: 0
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

  recentActivities: ActivityView[] = [];

  prochaineCampagne: {
    nom: string;
    dateDebut?: string;
    participants?: number;
    dureeJours?: number;
  } | null = null;

  constructor(
    private authService: AuthService,
    private dashboardService: DashboardService,
    private activiteService: ActiviteService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUser = user;
      }
    });
    // Reste synchronisé avec le flux partagé (ex: quand la cloche efface l'historique)
    this.activitiesSub = this.activiteService.activities$.subscribe(activities => {
      this.recentActivities = activities.slice(0, 8).map(toActivityView);
    });
    this.loadDashboard();
    this.routeSub = this.route.queryParams.subscribe(params => {
      const id = params['activityId'];
      if (id) {
        this.highlightedActivityId = +id;
        setTimeout(() => this.scrollToActivity(+id), 400);
      }
    });
  }

  ngAfterViewInit(): void {}

  ngOnDestroy(): void {
    this.activitiesSub?.unsubscribe();
    this.routeSub?.unsubscribe();
  }

  private scrollToActivity(activityId: number): void {
    const el = document.getElementById('activity-' + activityId);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } else if (this.activitySection) {
      this.activitySection.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  clearHighlight(): void {
    this.highlightedActivityId = null;
  }

  getDetailRoute(item: ActivityView): string | null {
    const type = item.type;
    if (type.startsWith('EMPLOYE_') && type !== 'EMPLOYE_SUPPRIME') return '/employees';
    if (type.startsWith('CAMPAGNE_') && type !== 'CAMPAGNE_SUPPRIMEE') return '/evaluations';
    if (type.startsWith('FICHE_')) return '/fiches';
    if (type === 'QUESTION_AJOUTEE') return '/evaluations';
    return null;
  }

  private loadDashboard(): void {
    this.loading = true;
    this.dashboardService.getStats().subscribe({
      next: stats => {
        this.applyStats(stats);
        this.loading = false;
        setTimeout(() => this.animateCounters(), 100);
      },
      error: () => {
        this.loading = false;
      }
    });
    this.activiteService.refresh(8).subscribe();
  }

  private applyStats(s: DashboardStats): void {
    this.stats = {
      totalEmployees: s.totalEmployees,
      activeEmployees: s.activeEmployees,
      totalEvaluations: s.totalCampagnes,
      openEvaluations: s.openCampagnes,
      completedEvaluations: s.completedEvaluations,
      pendingEvaluations: s.pendingEvaluations,
      averageNote: s.averageNote
    };

    this.prochaineCampagne = s.prochaineCampagneNom
      ? {
          nom: s.prochaineCampagneNom,
          dateDebut: s.prochaineCampagneDateDebut,
          participants: s.prochaineCampagneParticipants,
          dureeJours: s.prochaineCampagneDureeJours
        }
      : null;
  }

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Bonjour';
    if (hour < 18) return 'Bon après-midi';
    return 'Bonsoir';
  }

  get quickSummary(): string {
    return `${this.stats.activeEmployees} employés actifs · ${this.stats.openEvaluations} campagne(s) ouverte(s) · ${this.stats.pendingEvaluations} en attente`;
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
