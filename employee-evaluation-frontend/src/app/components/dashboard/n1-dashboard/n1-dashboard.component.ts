import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError, distinctUntilChanged, filter } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { EmployeeService } from '../../../services/employee.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';
import { Evaluation } from '../../../models/evaluation.model';
import { Employee } from '../../../models/employee.model';

@Component({
  selector: 'app-n1-dashboard',
  templateUrl: './n1-dashboard.component.html',
  styleUrls: ['./n1-dashboard.component.css']
})
export class N1DashboardComponent implements OnInit, AfterViewInit, OnDestroy {

  currentUser: any = null;
  loading = true;
  private sub = new Subscription();

  // Raw data
  openCampaigns: Evaluation[] = [];
  myEmployees: Employee[] = [];
  myFiches: FicheEvaluation[] = [];

  // Animated display counters
  display = { campaigns: 0, toEvaluate: 0, done: 0, progress: 0 };

  today = new Date();
  autoRefreshTimer: any;

  constructor(
    private authService: AuthService,
    private evaluationService: EvaluationService,
    private employeeService: EmployeeService,
    private ficheService: FicheService,
    private router: Router,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      this.authService.currentUser$.pipe(
        filter(user => !!user?.id && user.id !== 0),
        distinctUntilChanged((a, b) => a!.id === b!.id)
      ).subscribe(user => {
        this.currentUser = user;
        this.loadDashboard();
      })
    );
  }

  ngAfterViewInit(): void {
    // Auto-refresh every 60s to pick up newly opened campaigns
    this.autoRefreshTimer = setInterval(() => this.loadDashboard(), 60_000);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    if (this.autoRefreshTimer) clearInterval(this.autoRefreshTimer);
  }

  // ─── Data ────────────────────────────────────────────────────────────────

  loadDashboard(): void {
    if (!this.currentUser?.id) return;
    this.loading = true;

    forkJoin({
      evaluations: this.evaluationService.getAll().pipe(
        catchError(() => of([] as Evaluation[]))
      ),
      employees: this.employeeService.getByRole('EMPLOYE').pipe(
        catchError(() => of([] as Employee[]))
      ),
      fiches: this.ficheService.getByN1(this.currentUser.id).pipe(
        catchError(() => of([] as FicheEvaluation[]))
      )
    }).subscribe({
      next: ({ evaluations, employees, fiches }) => {
        this.openCampaigns = evaluations.filter(e => e.statut === 'OUVERTE');
        this.myEmployees   = employees;
        this.myFiches      = fiches;
        this.loading       = false;
        setTimeout(() => this.animateCounters(), 100);
      },
      error: () => {
        this.toastr.error('Erreur de chargement du tableau de bord', 'Erreur');
        this.loading = false;
      }
    });
  }

  // ─── Computed ────────────────────────────────────────────────────────────

  get greeting(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Bonjour' : h < 18 ? 'Bon après-midi' : 'Bonsoir';
  }

  get pendingCount(): number {
    // fiches not yet completed (no noteN1) per open campaign × employees
    const done = new Set(
      this.myFiches
        .filter(f => f.noteN1 !== null)
        .map(f => `${f.employeId}-${f.evaluationId}`)
    );
    let pending = 0;
    for (const campaign of this.openCampaigns) {
      for (const emp of this.myEmployees) {
        if (!done.has(`${emp.id}-${campaign.id}`)) pending++;
      }
    }
    return pending;
  }

  get doneCount(): number {
    return this.myFiches.filter(f => f.noteN1 !== null).length;
  }

  /** Fiches évaluées uniquement pour les campagnes actuellement ouvertes. */
  get doneCountOpen(): number {
    const openIds = new Set(this.openCampaigns.map(c => c.id));
    return this.myFiches.filter(f => f.noteN1 !== null && openIds.has(f.evaluationId)).length;
  }

  get totalToEvaluate(): number {
    return this.openCampaigns.length * this.myEmployees.length;
  }

  get progressPercent(): number {
    const total = this.totalToEvaluate;
    if (!total) return 0;
    const pct = Math.round((this.doneCountOpen / total) * 100);
    return Math.min(100, Math.max(0, pct));
  }

  getCampaignProgress(campaign: Evaluation): number {
    const done = this.myFiches.filter(
      f => f.evaluationId === campaign.id && f.noteN1 !== null
    ).length;
    if (!this.myEmployees.length) return 0;
    return Math.round((done / this.myEmployees.length) * 100);
  }

  getCampaignDone(campaign: Evaluation): number {
    return this.myFiches.filter(
      f => f.evaluationId === campaign.id && f.noteN1 !== null
    ).length;
  }

  getRemainingDays(dateFin: string): number {
    const diff = new Date(dateFin).getTime() - Date.now();
    return Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)));
  }

  getRemainingClass(days: number): string {
    if (days <= 2)  return 'urgent';
    if (days <= 7)  return 'warning';
    return 'normal';
  }

  isAllDone(campaign: Evaluation): boolean {
    return this.getCampaignProgress(campaign) === 100;
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  getFicheStatusForEmployee(emp: Employee): string {
    // Check across all open campaigns if this employee has a completed fiche
    const fiche = this.myFiches.find(f => f.employeId === emp.id);
    if (!fiche) return 'Non commencé';
    if (fiche.statut === 'CLOTUREE' || fiche.noteN1 !== null) return 'Terminé';
    return 'En cours';
  }

  trackById(_: number, item: any): number { return item.id; }

  // ─── Navigation ──────────────────────────────────────────────────────────

  evaluateCampaign(campaign: Evaluation): void {
    this.router.navigate(['/n1/evaluer', campaign.id]);
  }

  goToHistory(): void {
    this.router.navigate(['/n1/historique']);
  }

  // ─── Animation ───────────────────────────────────────────────────────────

  private animateCounters(): void {
    const targets = {
      campaigns:  this.openCampaigns.length,
      toEvaluate: this.pendingCount,
      done:       this.doneCount,
      progress:   this.progressPercent
    };
    const duration = 1200;
    const start = performance.now();
    const step = (now: number) => {
      const p     = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      this.display.campaigns  = Math.round(targets.campaigns  * eased);
      this.display.toEvaluate = Math.round(targets.toEvaluate * eased);
      this.display.done       = Math.round(targets.done       * eased);
      this.display.progress   = Math.round(targets.progress   * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
}
