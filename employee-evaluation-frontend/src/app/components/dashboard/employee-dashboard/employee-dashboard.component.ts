import { Component, OnInit, OnDestroy } from '@angular/core';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError, distinctUntilChanged, filter } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EmployeeService } from '../../../services/employee.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';
import { Employee } from '../../../models/employee.model';
import { Evaluation } from '../../../models/evaluation.model';

@Component({
  selector: 'app-employee-dashboard',
  templateUrl: './employee-dashboard.component.html',
  styleUrls: ['./employee-dashboard.component.css']
})
export class EmployeeDashboardComponent implements OnInit, OnDestroy {

  currentUser: any = null;
  employee: Employee | null = null;
  loading = true;
  private sub = new Subscription();
  private autoRefreshTimer: any;

  fiches: FicheEvaluation[] = [];
  openCampaignsCount = 0;

  // Animated display counters
  display = { avgNote: 0, closed: 0, pending: 0, openCampaigns: 0 };

  constructor(
    private authService: AuthService,
    private employeeService: EmployeeService,
    private evaluationService: EvaluationService,
    private ficheService: FicheService,
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
    this.autoRefreshTimer = setInterval(() => this.loadDashboard(true), 60_000);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    if (this.autoRefreshTimer) clearInterval(this.autoRefreshTimer);
  }

  // ─── Data ────────────────────────────────────────────────────────────────

  loadDashboard(silent = false): void {
    if (!silent) this.loading = true;

    forkJoin({
      employee: this.employeeService.getById(this.currentUser.id).pipe(catchError(() => of(null))),
      fiches: this.ficheService.getByEmploye(this.currentUser.id).pipe(catchError(() => of([] as FicheEvaluation[]))),
      evaluations: this.evaluationService.getAll().pipe(catchError(() => of([] as Evaluation[])))
    }).subscribe({
      next: ({ employee, fiches, evaluations }) => {
        this.employee = employee;
        this.fiches = fiches;
        this.openCampaignsCount = evaluations.filter(e => e.statut === 'OUVERTE').length;
        this.loading = false;
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

  get closedFiches(): FicheEvaluation[] {
    return this.fiches.filter(f => f.statut === 'CLOTUREE');
  }

  get pendingFiches(): FicheEvaluation[] {
    return this.fiches.filter(f => f.statut === 'EN_ATTENTE_EMPLOYE');
  }

  get recentClosed(): FicheEvaluation[] {
    return [...this.closedFiches]
      .sort((a, b) => new Date(b.dateCreation).getTime() - new Date(a.dateCreation).getTime())
      .slice(0, 5);
  }

  get avgNote(): number {
    const notes = this.closedFiches.map(f => f.noteFinale).filter((n): n is number => n !== null);
    if (!notes.length) return 0;
    return Math.round((notes.reduce((s, n) => s + n, 0) / notes.length) * 10) / 10;
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  getInitials(prenom?: string | null, nom?: string | null): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  noteClass(note: number | null): string {
    if (note === null) return 'note-neutral';
    if (note >= 7) return 'note-good';
    if (note >= 5) return 'note-mid';
    return 'note-low';
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  trackById(_: number, item: any): number { return item.id; }

  // ─── Animation ───────────────────────────────────────────────────────────

  private animateCounters(): void {
    const targets = {
      avgNote: this.avgNote,
      closed: this.closedFiches.length,
      pending: this.pendingFiches.length,
      openCampaigns: this.openCampaignsCount
    };
    const duration = 1200;
    const start = performance.now();
    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      this.display.avgNote = Math.round(targets.avgNote * eased * 10) / 10;
      this.display.closed = Math.round(targets.closed * eased);
      this.display.pending = Math.round(targets.pending * eased);
      this.display.openCampaigns = Math.round(targets.openCampaigns * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
}
