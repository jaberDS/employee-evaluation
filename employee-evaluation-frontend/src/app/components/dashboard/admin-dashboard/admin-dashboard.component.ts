import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { DashboardService, DashboardStats } from '../../../services/dashboard.service';
import { ActiviteDetail, ActiviteEtape, ActiviteService } from '../../../services/activite.service';
import { ActivityView, toActivityView } from '../../../shared/activity-view';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

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
  styleUrls: ['./admin-dashboard.component.css'],
  animations: [
    trigger('backdropFade', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('220ms ease-out', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0 }))
      ])
    ]),
    trigger('modalPop', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(16px) scale(0.94)' }),
        animate('320ms cubic-bezier(0.34, 1.56, 0.64, 1)',
          style({ opacity: 1, transform: 'translateY(0) scale(1)' }))
      ]),
      transition(':leave', [
        style({ opacity: 1, transform: 'translateY(0) scale(1)' }),
        animate('180ms ease-in', style({ opacity: 0, transform: 'translateY(8px) scale(0.97)' }))
      ])
    ])
  ]
})
export class AdminDashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('activitySection') activitySection!: ElementRef;

  currentUser: any = null;
  today = new Date();
  loading = true;
  highlightedActivityId: number | null = null;

  /** Modale de détail : déroulé du workflow de l'enregistrement concerné. */
  detail: ActiviteDetail | null = null;
  detailLoading = false;
  detailError: string | null = null;
  showDetail = false;

  /** Fiche concernée par l'activité ouverte — permet d'afficher ses questions. */
  detailFicheId: number | null = null;

  /** Fiche dont les questions sont affichées (null = modale fermée). */
  questionsFicheId: number | null = null;

  private activitiesSub?: Subscription;
  private routeSub?: Subscription;
  private detailSub?: Subscription;

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
    this.detailSub?.unsubscribe();
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

  /** Les activités antérieures à cette fonctionnalité n'ont pas d'enregistrement rattaché. */
  hasDetail(item: ActivityView): boolean {
    return !!item.entiteType && !!item.entiteId;
  }

  openDetail(item: ActivityView, event?: Event): void {
    event?.stopPropagation();
    this.showDetail = true;
    this.detailLoading = true;
    this.detailError = null;
    this.detail = null;
    this.detailFicheId = item.entiteType === 'FICHE' ? (item.entiteId ?? null) : null;

    this.detailSub?.unsubscribe();
    this.detailSub = this.activiteService.getDetail(item.id).subscribe({
      next: detail => {
        this.detail = detail;
        this.detailLoading = false;
      },
      error: () => {
        this.detailError = "Impossible de charger le détail de cette activité.";
        this.detailLoading = false;
      }
    });
  }

  closeDetail(): void {
    this.showDetail = false;
    this.detail = null;
    this.detailError = null;
    this.detailFicheId = null;
    this.detailSub?.unsubscribe();
  }

  etapeIcon(etape: ActiviteEtape): IconName {
    return (etape.icone || 'activity') as IconName;
  }

  /** Vert ≥ 8, orange ≥ 5, rouge en dessous — même convention que la fiche de détail. */
  noteClass(note?: number): string {
    if (note == null) return '';
    if (note >= 8) return 'note-high';
    if (note >= 5) return 'note-mid';
    return 'note-low';
  }

  statutLabel(statut?: string): string {
    const labels: Record<string, string> = {
      EN_ATTENTE: 'En attente',
      EN_COURS_N1: 'En cours N+1',
      EN_ATTENTE_N2: 'En attente N+2',
      EN_ATTENTE_EMPLOYE: 'En attente employé',
      CLOTUREE: 'Clôturée',
      A_REVISER: 'À réviser',
      BROUILLON: 'Brouillon',
      OUVERTE: 'Ouverte',
      FERMEE: 'Fermée',
      ACTIF: 'Actif',
      INACTIF: 'Inactif'
    };
    return statut ? (labels[statut] ?? statut) : '';
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
