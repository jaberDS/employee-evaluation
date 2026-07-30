import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest, forkJoin, interval, Subscription } from 'rxjs';
import { distinctUntilChanged, filter, map } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { EmployeeService } from '../../../services/employee.service';
import { FicheService } from '../../../services/fiche.service';
import { Evaluation, Question, TypeAffectation } from '../../../models/evaluation.model';
import { Employee } from '../../../models/employee.model';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

@Component({
  selector: 'app-n1-fiche-evaluation',
  templateUrl: './fiche-evaluation.component.html',
  styleUrls: ['./fiche-evaluation.component.css']
})
export class N1FicheEvaluationComponent implements OnInit, OnDestroy {

  currentUser: any = null;
  loading = true;
  submitting = false;
  submitted = false;
  showSuccess = false;
  autoSaveLabel = '';

  campaignId!: number;
  employeeId!: number;
  campaign: Evaluation | null = null;
  employee: Employee | null = null;
  questions: Question[] = [];

  // Answers map: questionId → answer value (number for NOTE, boolean for OUI_NON, string for text)
  answers: { [questionId: number]: any } = {};
  // Raw text typed in NOTE inputs (preserves "7." while typing so decimals survive)
  scoreInputs: { [questionId: number]: string } = {};
  commentaire = '';

  unansweredIds: number[] = [];

  private autoSaveSub?: Subscription;
  private routeSub?: Subscription;
  private autoSaveInterval = 30_000; // 30s

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
    this.routeSub = combineLatest([
      this.authService.currentUser$.pipe(
        filter(user => !!user?.id && user.id !== 0),
        distinctUntilChanged((a, b) => a!.id === b!.id)
      ),
      this.route.params
    ]).subscribe(([user, params]) => {
      this.currentUser = user;
      this.campaignId  = +params['campaignId'];
      this.employeeId  = +params['employeeId'];
      this.loadData();
    });
  }

  ngOnDestroy(): void {
    this.autoSaveSub?.unsubscribe();
    this.routeSub?.unsubscribe();
  }

  // ─── Load ─────────────────────────────────────────────────────────────────

  loadData(): void {
    this.loading = true;
    forkJoin({
      campaign:  this.evaluationService.getById(this.campaignId),
      employee: this.employeeService.getSousN1(this.currentUser.id).pipe(
        map(employees => employees.find(emp => emp.id === this.employeeId) ?? null)
      ),
      questions: this.evaluationService.getQuestions(this.campaignId)
    }).subscribe({
      next: ({ campaign, employee, questions }) => {
        this.campaign  = campaign;
        this.employee  = employee;
        if (!employee) {
          this.toastr.warning('Employé introuvable dans la liste des employés', 'Attention');
        }
        this.questions = questions.sort((a, b) => a.ordre - b.ordre);

        // Pre-load any existing draft answers
        this.ficheService.getByEvaluation(this.campaignId).subscribe(fiches => {
          const existing = fiches.find(f => f.employeId === this.employeeId);
          if (existing?.reponsesN1) {
            Object.entries(existing.reponsesN1).forEach(([k, v]) => {
              this.answers[+k] = v;
              this.scoreInputs[+k] = String(v);
            });
          }
        });

        this.loading = false;
        this.startAutoSave();
      },
      error: () => {
        this.toastr.error('Erreur de chargement du formulaire', 'Erreur');
        this.loading = false;
      }
    });
  }

  // ─── Auto-save ────────────────────────────────────────────────────────────

  private startAutoSave(): void {
    this.autoSaveSub = interval(this.autoSaveInterval).subscribe(() => {
      if (Object.keys(this.answers).length > 0) {
        this.autoSaveLabel = 'Sauvegarde automatique...';
        setTimeout(() => { this.autoSaveLabel = 'Brouillon sauvegardé ✓'; }, 800);
        setTimeout(() => { this.autoSaveLabel = ''; }, 3000);
      }
    });
  }

  // ─── Aiguillage de présentation ───────────────────────────────────────────

  /**
   * L'employé porte l'affectation de référence ; la campagne sert de repli
   * tant que la liste des subordonnés n'est pas revenue.
   */
  get typeAffectation(): TypeAffectation {
    return this.employee?.typeAffectation ?? this.campaign?.typeAffectation ?? 'SIEGE';
  }

  // ─── Answer helpers ───────────────────────────────────────────────────────

  setAnswer(questionId: number, value: any): void {
    this.answers[questionId] = value;
    this.unansweredIds = this.unansweredIds.filter(id => id !== questionId);
  }

  /** Relais depuis les composants de présentation. */
  onAnswerChange(e: { questionId: number; value: any }): void {
    this.setAnswer(e.questionId, e.value);
    // Garde l'input de saisie précise aligné sur l'échelle / le curseur
    if (typeof e.value === 'number') {
      this.scoreInputs[e.questionId] = String(e.value);
    }
  }

  onScoreInput(e: { questionId: number; event: Event; noteMax: number }): void {
    this.setScore(e.questionId, e.event, e.noteMax);
  }

  getRatingValue(questionId: number): number {
    return this.answers[questionId] ?? 0;
  }

  /** Saisie d'une note décimale libre (ex. 7.25), bornée entre 0 et noteMax. */
  setScore(questionId: number, event: Event, noteMax: number): void {
    const input = event.target as HTMLInputElement;
    // N'autorise que les chiffres et un seul séparateur décimal (. ou ,)
    let raw = input.value.replace(/[^0-9.,]/g, '');
    const firstSep = raw.search(/[.,]/);
    if (firstSep !== -1) {
      // Garde le premier séparateur, retire les suivants
      raw = raw.slice(0, firstSep + 1) + raw.slice(firstSep + 1).replace(/[.,]/g, '');
    }
    if (raw !== input.value) {
      input.value = raw; // reflète la version nettoyée
    }
    this.scoreInputs[questionId] = raw;

    if (raw.trim() === '') {
      delete this.answers[questionId];
      return;
    }
    let value = parseFloat(raw.replace(',', '.'));
    if (isNaN(value)) {
      delete this.answers[questionId];
      return;
    }
    const max = noteMax ?? 10;
    if (value < 0) value = 0;
    if (value > max) value = max;
    // Arrondi à 2 décimales
    value = Math.round(value * 100) / 100;
    this.answers[questionId] = value;
    this.unansweredIds = this.unansweredIds.filter(id => id !== questionId);
  }

  getTextValue(questionId: number): string {
    return this.answers[questionId] ?? '';
  }

  getOuiNonValue(questionId: number): boolean | null {
    return this.answers[questionId] ?? null;
  }

  get answeredCount(): number {
    return this.questions.filter(q => this.answers[q.id!] !== undefined && this.answers[q.id!] !== '').length;
  }

  get progressPct(): number {
    if (!this.questions.length) return 0;
    return Math.round((this.answeredCount / this.questions.length) * 100);
  }

  get requiredUnanswered(): Question[] {
    return this.questions.filter(
      q => q.obligatoire && (this.answers[q.id!] === undefined || this.answers[q.id!] === '')
    );
  }

  isUnanswered(q: Question): boolean {
    return this.unansweredIds.includes(q.id!);
  }

  // ─── Submit ───────────────────────────────────────────────────────────────

  saveDraft(): void {
    this.autoSaveLabel = 'Sauvegarde...';
    setTimeout(() => { this.autoSaveLabel = 'Brouillon sauvegardé ✓'; }, 600);
    setTimeout(() => { this.autoSaveLabel = ''; }, 3000);
    this.toastr.info('Brouillon sauvegardé localement', '', { timeOut: 1800 });
  }

  submitEvaluation(): void {
    // Validate required questions
    const missing = this.requiredUnanswered;
    if (missing.length > 0) {
      this.unansweredIds = missing.map(q => q.id!);
      this.toastr.warning(
        `${missing.length} question(s) obligatoire(s) sans réponse`,
        'Attention'
      );
      // Scroll to first unanswered
      setTimeout(() => {
        const el = document.getElementById(`q-${missing[0].id}`);
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 100);
      return;
    }

    this.submitting = true;

    // Convert answers: for NOTE type keep numeric; for OUI_NON: true→noteMax, false→0; for text: 0
    const reponses: { [key: number]: number } = {};
    this.questions.forEach(q => {
      const val = this.answers[q.id!];
      if (q.typeQuestion === 'NOTE') {
        reponses[q.id!] = Number(val) || 0;
      } else if (q.typeQuestion === 'OUI_NON') {
        reponses[q.id!] = val === true ? (q.noteMax ?? 10) : 0;
      } else {
        // TEXTE / COMMENTAIRE — store as 0 for note calculation, text stored separately
        reponses[q.id!] = 0;
      }
    });

    this.ficheService.evaluerParN1({
      employeId:   this.employeeId,
      evaluationId: this.campaignId,
      reponses,
      commentaire: this.commentaire
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.showSuccess = true;
        this.autoSaveSub?.unsubscribe();
      },
      error: (err) => {
        this.submitting = false;
        const msg = err?.error?.message ?? 'Erreur lors de la soumission';
        this.toastr.error(msg, 'Erreur');
      }
    });
  }

  goBack(): void {
    // Navigate back and force reload by adding a timestamp query param
    this.router.navigate(['/n1/evaluer', this.campaignId], {
      queryParams: { reload: Date.now() }
    });
  }

  goToHistory(): void {
    this.router.navigate(['/n1/historique']);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  getTypeLabel(type: string): string {
    const map: any = { NOTE: 'Note', OUI_NON: 'Oui / Non', TEXTE: 'Texte', COMMENTAIRE: 'Commentaire' };
    return map[type] ?? type;
  }

  getTypeIcon(type: string): IconName {
    const map: Record<string, IconName> = {
      NOTE: 'bar-chart-3', OUI_NON: 'toggle-left', TEXTE: 'type', COMMENTAIRE: 'message-square'
    };
    return map[type] ?? 'help-circle';
  }

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }
}
