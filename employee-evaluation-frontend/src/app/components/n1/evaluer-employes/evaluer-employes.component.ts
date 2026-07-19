import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest, forkJoin, of, Subscription } from 'rxjs';
import { catchError, distinctUntilChanged, filter } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { EmployeeService } from '../../../services/employee.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';
import { Evaluation } from '../../../models/evaluation.model';
import { Employee } from '../../../models/employee.model';

export interface EmployeeCard {
  employee: Employee;
  fiche: FicheEvaluation | null;
  status: 'non_commence' | 'en_cours' | 'termine';
  progressPct: number;
}

@Component({
  selector: 'app-evaluer-employes',
  templateUrl: './evaluer-employes.component.html',
  styleUrls: ['./evaluer-employes.component.css']
})
export class EvaluerEmployesComponent implements OnInit, OnDestroy {

  step: 'campaigns' | 'employees' = 'campaigns';

  currentUser: any = null;
  loading = true;
  private sub = new Subscription();

  openCampaigns: Evaluation[] = [];
  selectedCampaign: Evaluation | null = null;
  myEmployees: Employee[] = [];
  myFiches: FicheEvaluation[] = [];
  employeeCards: EmployeeCard[] = [];

  searchTerm = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private evaluationService: EvaluationService,
    private employeeService: EmployeeService,
    private ficheService: FicheService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      combineLatest([
        this.authService.currentUser$.pipe(
          filter(user => !!user?.id && user.id !== 0),
          distinctUntilChanged((a, b) => a!.id === b!.id)
        ),
        this.route.params,
        this.route.queryParams  // re-trigger when reload param changes
      ]).subscribe(([user, params]) => {
        this.currentUser = user;
        const preselectedCampaignId = params['campaignId'] ? +params['campaignId'] : null;
        this.loadAll(preselectedCampaignId);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadAll(preselectedCampaignId: number | null): void {
    if (!this.currentUser?.id) return;
    this.loading = true;

    // Remember the currently selected campaign so we can restore it after reload
    const activeCampaignId = this.selectedCampaign?.id ?? preselectedCampaignId;

    forkJoin({
      evaluations: this.evaluationService.getAll().pipe(
        catchError(() => of([] as Evaluation[]))
      ),
      employees: this.employeeService.getSousN1(this.currentUser.id).pipe(
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

        // Restore the selected campaign and rebuild cards with fresh data
        if (activeCampaignId) {
          const found = this.openCampaigns.find(e => e.id === activeCampaignId);
          if (found) {
            this.selectedCampaign = found;
            this.step = 'employees';
            this.buildCards();
          }
        }
      },
      error: () => {
        this.toastr.error('Erreur de chargement', 'Erreur');
        this.loading = false;
      }
    });
  }

  selectCampaign(ev: Evaluation): void {
    this.selectedCampaign = ev;
    this.step = 'employees';
    this.buildCards();
    this.searchTerm = '';
  }

  backToCampaigns(): void {
    this.step = 'campaigns';
    this.selectedCampaign = null;
    this.employeeCards = [];
    this.searchTerm = '';
  }

  buildCards(): void {
    if (!this.selectedCampaign) { this.employeeCards = []; return; }
    const campId = this.selectedCampaign.id!;

    this.employeeCards = this.myEmployees.map(emp => {
      const fiche = this.myFiches.find(
        f => f.employeId === emp.id && f.evaluationId === campId
      ) ?? null;

      let status: 'non_commence' | 'en_cours' | 'termine' = 'non_commence';
      if (fiche) {
        status = fiche.noteN1 !== null ? 'termine' : 'en_cours';
      }

      let progressPct = 0;
      if (status === 'termine') progressPct = 100;
      else if (status === 'en_cours' && fiche?.reponsesN1) {
        const answered = Object.keys(fiche.reponsesN1).length;
        const total    = this.selectedCampaign?.questions?.length ?? 1;
        progressPct    = Math.round((answered / total) * 100);
      }

      return { employee: emp, fiche, status, progressPct };
    });
  }

  get filteredCards(): EmployeeCard[] {
    if (!this.searchTerm.trim()) return this.employeeCards;
    const t = this.searchTerm.toLowerCase();
    return this.employeeCards.filter(c =>
      (c.employee.prenom + ' ' + c.employee.nom).toLowerCase().includes(t) ||
      c.employee.email.toLowerCase().includes(t)
    );
  }

  get doneCount(): number    { return this.employeeCards.filter(c => c.status === 'termine').length; }
  get pendingCount(): number { return this.employeeCards.filter(c => c.status !== 'termine').length; }
  get allDone(): boolean     { return this.employeeCards.length > 0 && this.pendingCount === 0; }

  getCampaignDone(ev: Evaluation): number {
    return this.myFiches.filter(f => f.evaluationId === ev.id && f.noteN1 !== null).length;
  }

  getCampaignProgress(ev: Evaluation): number {
    if (!this.myEmployees.length) return 0;
    return Math.round((this.getCampaignDone(ev) / this.myEmployees.length) * 100);
  }

  getRemainingDays(dateFin: string): number {
    return Math.max(0, Math.ceil((new Date(dateFin).getTime() - Date.now()) / 86_400_000));
  }

  getRemainingClass(days: number): string {
    if (days <= 2) return 'urgent';
    if (days <= 7) return 'warning';
    return 'normal';
  }

  startEvaluation(card: EmployeeCard): void {
    if (!this.selectedCampaign) return;
    this.router.navigate(['/n1/evaluer', this.selectedCampaign.id, 'employe', card.employee.id]);
  }

  viewFiche(card: EmployeeCard): void {
    this.router.navigate(['/n1/historique']);
  }

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  getStatusLabel(s: string): string {
    return s === 'termine' ? 'Terminé' : s === 'en_cours' ? 'En cours' : 'Non commencé';
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  trackById(_: number, item: any): number { return item.id ?? item.employee?.id; }
}
