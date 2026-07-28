import { Component, HostListener, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';

@Component({
  selector: 'app-n1-historique',
  templateUrl: './historique.component.html',
  styleUrls: ['./historique.component.css']
})
export class N1HistoriqueComponent implements OnInit {

  currentUser: any = null;
  loading = true;
  fiches: FicheEvaluation[] = [];

  searchTerm = '';
  statusFilter = 'ALL';
  currentPage = 1;
  pageSize = 8;

  // Detail modal
  selectedFiche: FicheEvaluation | null = null;
  showDetail = false;

  // Fiche dont les questions sont affichées (null = modale fermée)
  questionsFicheId: number | null = null;

  // Delete confirmation modal
  confirmTarget: FicheEvaluation | 'ALL' | null = null;
  deleting = false;

  readonly STATUS_LABELS: Record<string, string> = {
    EN_ATTENTE:          'En attente',
    EN_COURS_N1:         'En cours',
    EN_ATTENTE_N2:       'En attente N+2',
    A_REVISER:           'À réviser',
    EN_ATTENTE_EMPLOYE:  'En attente employé',
    CLOTUREE:            'Clôturée'
  };

  constructor(
    private authService: AuthService,
    private ficheService: FicheService,
    private router: Router,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadFiches();
  }

  loadFiches(): void {
    if (!this.currentUser?.id) return;
    this.loading = true;
    this.ficheService.getByN1(this.currentUser.id).subscribe({
      next: (data) => {
        // Only show completed (noteN1 not null)
        this.fiches = data.filter(f => f.noteN1 !== null)
          .sort((a, b) => new Date(b.dateCreation).getTime() - new Date(a.dateCreation).getTime());
        this.loading = false;
      },
      error: () => {
        this.toastr.error('Erreur de chargement de l\'historique', 'Erreur');
        this.loading = false;
      }
    });
  }

  get filtered(): FicheEvaluation[] {
    let r = [...this.fiches];
    if (this.searchTerm.trim()) {
      const t = this.searchTerm.toLowerCase();
      r = r.filter(f =>
        (f.employePrenom + ' ' + f.employeNom).toLowerCase().includes(t) ||
        f.evaluationNom.toLowerCase().includes(t)
      );
    }
    if (this.statusFilter !== 'ALL') {
      r = r.filter(f => f.statut === this.statusFilter);
    }
    return r;
  }

  get paginated(): FicheEvaluation[] {
    const s = (this.currentPage - 1) * this.pageSize;
    return this.filtered.slice(s, s + this.pageSize);
  }

  get totalPages(): number { return Math.max(1, Math.ceil(this.filtered.length / this.pageSize)); }
  get pageNumbers(): number[] { return Array.from({ length: this.totalPages }, (_, i) => i + 1); }

  goToPage(p: number): void { if (p >= 1 && p <= this.totalPages) this.currentPage = p; }
  clearFilters(): void { this.searchTerm = ''; this.statusFilter = 'ALL'; this.currentPage = 1; }

  openDetail(f: FicheEvaluation): void { this.selectedFiche = f; this.showDetail = true; }
  closeDetail(): void                  { this.showDetail = false; this.selectedFiche = null; }

  /** Une fiche renvoyée en révision par le N+2 peut être re-modifiée par le N+1. */
  canRevise(f: FicheEvaluation): boolean {
    return f.statut === 'A_REVISER';
  }

  reviseFiche(f: FicheEvaluation): void {
    this.router.navigate(['/n1/evaluer', f.evaluationId, 'employe', f.employeId]);
  }

  /** Suppression autorisée uniquement si la campagne est clôturée et que le N+2 et l'employé ont tous deux confirmé. */
  canDelete(f: FicheEvaluation): boolean {
    return f.evaluationStatut === 'CLOTUREE'
      && f.decisionN2 === 'ACCEPTEE'
      && f.decisionEmploye === 'ACCEPTEE';
  }

  get eligibleForDeletion(): FicheEvaluation[] {
    return this.fiches.filter(f => this.canDelete(f));
  }

  askDelete(f: FicheEvaluation): void { this.confirmTarget = f; }
  askDeleteAll(): void { this.confirmTarget = 'ALL'; }
  cancelDelete(): void { if (!this.deleting) this.confirmTarget = null; }

  confirmDelete(): void {
    if (!this.confirmTarget || !this.currentUser?.id) return;
    this.deleting = true;

    if (this.confirmTarget === 'ALL') {
      this.ficheService.deleteAllEligibleByN1(this.currentUser.id).subscribe({
        next: (count) => {
          this.toastr.success(`${count} évaluation(s) supprimée(s) de l'historique`, 'Succès');
          this.deleting = false;
          this.confirmTarget = null;
          this.loadFiches();
        },
        error: (err) => {
          this.deleting = false;
          this.toastr.error(err?.error?.message ?? 'Erreur lors de la suppression', 'Erreur');
        }
      });
    } else {
      const target = this.confirmTarget;
      this.ficheService.delete(target.id).subscribe({
        next: () => {
          this.toastr.success('Évaluation supprimée de l\'historique', 'Succès');
          this.deleting = false;
          this.confirmTarget = null;
          this.fiches = this.fiches.filter(f => f.id !== target.id);
        },
        error: (err) => {
          this.deleting = false;
          this.toastr.error(err?.error?.message ?? 'Erreur lors de la suppression', 'Erreur');
        }
      });
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    // La modale des questions se ferme elle-même et reste au-dessus des autres
    if (this.questionsFicheId !== null) return;
    if (this.showDetail) this.closeDetail();
    if (this.confirmTarget) this.cancelDelete();
  }

  getStatusLabel(s: string): string  { return this.STATUS_LABELS[s] ?? s; }
  getStatusClass(s: string): string  {
    const map: Record<string,string> = {
      CLOTUREE:           'hist-status-done',
      EN_ATTENTE_N2:      'hist-status-pending',
      A_REVISER:          'hist-status-revise',
      EN_ATTENTE_EMPLOYE: 'hist-status-emp',
      EN_COURS_N1:        'hist-status-progress'
    };
    return map[s] ?? 'hist-status-default';
  }

  getNoteClass(note: number | null): string {
    if (note === null) return '';
    if (note >= 8) return 'note-excellent';
    if (note >= 6) return 'note-good';
    if (note >= 4) return 'note-average';
    return 'note-low';
  }

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('fr-FR', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' });
  }

  trackById(_: number, f: FicheEvaluation): number { return f.id; }
}
