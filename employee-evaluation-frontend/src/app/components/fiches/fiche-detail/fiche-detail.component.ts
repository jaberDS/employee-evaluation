import { Component, HostListener, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';
import { Question } from '../../../models/evaluation.model';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

interface QuestionRow {
  question: Question;
  mark: number | null;
}

@Component({
  selector: 'app-fiche-detail',
  templateUrl: './fiche-detail.component.html',
  styleUrls: ['./fiche-detail.component.css']
})
export class FicheDetailComponent implements OnInit {

  loading = true;
  notFound = false;
  fiche: FicheEvaluation | null = null;
  rows: QuestionRow[] = [];

  // Decision modal state
  modalOpen = false;
  modalDecision: 'ACCEPT' | 'REJECT' | null = null;
  modalComment = '';
  submitting = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private evaluationService: EvaluationService,
    private ficheService: FicheService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.params['id'];
    this.load(id);
  }

  private load(id: number): void {
    this.loading = true;
    this.ficheService.getById(id).pipe(
      catchError(() => of(null))
    ).subscribe(fiche => {
      if (!fiche) {
        this.notFound = true;
        this.loading = false;
        return;
      }

      const user = this.authService.getUser();
      if (this.authService.getRole() === 'EMPLOYE' && user?.id !== fiche.employeId) {
        this.router.navigate(['/fiches']);
        return;
      }

      this.fiche = fiche;
      this.evaluationService.getQuestions(fiche.evaluationId).pipe(
        catchError(() => of([] as Question[]))
      ).subscribe(questions => {
        this.rows = questions
          .sort((a, b) => a.ordre - b.ordre)
          .map(question => ({
            question,
            mark: fiche.reponsesN1 ? (fiche.reponsesN1[question.id!] ?? null) : null
          }));
        this.loading = false;
      });
    });
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  ouiNonLabel(row: QuestionRow): string {
    if (row.mark === null) return '—';
    return row.mark === row.question.noteMax ? 'Oui' : 'Non';
  }

  getTypeIcon(type: string): IconName {
    const map: Record<string, IconName> = {
      NOTE: 'bar-chart-3', OUI_NON: 'toggle-left', TEXTE: 'type', COMMENTAIRE: 'message-square'
    };
    return map[type] ?? 'help-circle';
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

  trackById(_: number, item: QuestionRow): number { return item.question.id!; }

  get canDecide(): boolean {
    return this.fiche?.statut === 'EN_ATTENTE_EMPLOYE';
  }

  // ─── Decision modal ──────────────────────────────────────────────────────

  openDecision(decision: 'ACCEPT' | 'REJECT'): void {
    this.modalDecision = decision;
    this.modalComment = '';
    this.modalOpen = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.modalOpen = false;
    this.modalDecision = null;
    this.modalComment = '';
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.modalOpen) this.closeModal();
  }

  confirmDecision(): void {
    if (!this.fiche || !this.modalDecision) return;
    const accepte = this.modalDecision === 'ACCEPT';

    if (!accepte && this.modalComment.trim().length === 0) {
      this.toastr.warning('Un commentaire est requis pour un refus', 'Attention');
      return;
    }

    this.submitting = true;
    this.ficheService.validerParEmploye(this.fiche.id, accepte, this.modalComment.trim() || undefined).subscribe({
      next: (updated) => {
        this.submitting = false;
        this.modalOpen = false;
        this.modalDecision = null;
        this.fiche = updated;
        this.toastr.success(
          accepte ? 'Évaluation acceptée et clôturée' : 'Évaluation renvoyée en révision',
          'Succès'
        );
      },
      error: (err) => {
        this.submitting = false;
        const msg = err?.error?.message ?? 'Erreur lors de la validation';
        this.toastr.error(msg, 'Erreur');
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/fiches']);
  }
}
