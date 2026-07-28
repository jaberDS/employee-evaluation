import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError, distinctUntilChanged, filter } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';
import { Evaluation } from '../../../models/evaluation.model';

@Component({
  selector: 'app-n2-valider',
  templateUrl: './n2-valider.component.html',
  styleUrls: ['./n2-valider.component.css']
})
export class N2ValiderComponent implements OnInit, OnDestroy {

  loading = true;
  currentUser: any = null;
  private sub = new Subscription();
  private autoRefreshTimer: any;

  // Fiches EN_ATTENTE_N2, restricted to OPEN campaigns
  pendingFiches: FicheEvaluation[] = [];

  // Decision modal state
  modalOpen = false;
  modalFiche: FicheEvaluation | null = null;
  modalDecision: 'ACCEPT' | 'REJECT' | null = null;
  modalComment = '';
  submitting = false;

  // Fiche dont les questions sont affichées (null = modale fermée)
  questionsFicheId: number | null = null;

  constructor(
    private authService: AuthService,
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
        this.load();
      })
    );
    this.autoRefreshTimer = setInterval(() => this.load(true), 60_000);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    if (this.autoRefreshTimer) clearInterval(this.autoRefreshTimer);
  }

  // ─── Data ────────────────────────────────────────────────────────────────

  load(silent = false): void {
    if (!this.currentUser?.id) return;
    if (!silent) this.loading = true;

    forkJoin({
      evaluations: this.evaluationService.getAll().pipe(catchError(() => of([] as Evaluation[]))),
      pending: this.ficheService.getByN2(this.currentUser.id, 'EN_ATTENTE_N2')
        .pipe(catchError(() => of([] as FicheEvaluation[])))
    }).subscribe({
      next: ({ evaluations, pending }) => {
        const openIds = new Set(evaluations.filter(e => e.statut === 'OUVERTE').map(e => e.id));
        this.pendingFiches = pending
          .filter(f => openIds.has(f.evaluationId))
          .sort((a, b) => new Date(a.dateCreation).getTime() - new Date(b.dateCreation).getTime());
        this.loading = false;
      },
      error: () => {
        this.toastr.error('Erreur de chargement des évaluations', 'Erreur');
        this.loading = false;
      }
    });
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  noteClass(note: number | null): string {
    if (note === null) return 'note-neutral';
    if (note >= 7) return 'note-good';
    if (note >= 5) return 'note-mid';
    return 'note-low';
  }

  trackById(_: number, item: any): number { return item.id; }

  // ─── Decision modal ──────────────────────────────────────────────────────

  openDecision(fiche: FicheEvaluation, decision: 'ACCEPT' | 'REJECT'): void {
    this.modalFiche = fiche;
    this.modalDecision = decision;
    this.modalComment = '';
    this.modalOpen = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.modalOpen = false;
    this.modalFiche = null;
    this.modalDecision = null;
    this.modalComment = '';
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.modalOpen) this.closeModal();
  }

  confirmDecision(): void {
    if (!this.modalFiche || !this.modalDecision) return;
    const accepte = this.modalDecision === 'ACCEPT';

    if (!accepte && this.modalComment.trim().length === 0) {
      this.toastr.warning('Un commentaire est requis pour un refus', 'Attention');
      return;
    }

    this.submitting = true;
    this.ficheService.validerParN2(this.modalFiche.id, {
      accepte,
      commentaire: this.modalComment.trim()
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.modalOpen = false;
        this.toastr.success(
          accepte ? 'Évaluation validée avec succès' : 'Évaluation renvoyée en révision',
          'Succès'
        );
        this.modalFiche = null;
        this.modalDecision = null;
        this.load(true);
      },
      error: (err) => {
        this.submitting = false;
        const msg = err?.error?.message ?? 'Erreur lors de la validation';
        this.toastr.error(msg, 'Erreur');
      }
    });
  }
}
