import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { tap } from 'rxjs';
import { EvaluationService } from '../../../services/evaluation.service';
import { Evaluation } from '../../../models/evaluation.model';
import { ConfirmService } from '../../../shared/confirm/confirm.service';

type SortColumn = 'nomEvaluation' | 'dateDebut' | 'dateFin' | 'statut';
type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-evaluation-list',
  templateUrl: './evaluation-list.component.html',
  styleUrls: ['./evaluation-list.component.css']
})
export class EvaluationListComponent implements OnInit, AfterViewInit {
  evaluations: Evaluation[] = [];
  loading = true;
  searchTerm = '';
  statusFilter = 'ALL';
  dateFilter = 'ALL';
  sortColumn: SortColumn = 'dateDebut';
  sortDirection: SortDirection = 'desc';
  currentPage = 1;
  pageSize = 6;

  displayCounts = { total: 0, draft: 0, open: 0, closed: 0, done: 0 };

  constructor(
    private evalService: EvaluationService,
    private toastr: ToastrService,
    private confirmService: ConfirmService
  ) {}

  ngOnInit(): void {
    this.loadEvaluations();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.animateCounts(), 200);
  }

  get filteredEvaluations(): Evaluation[] {
    let result = [...this.evaluations];

    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase().trim();
      result = result.filter(ev => ev.nomEvaluation.toLowerCase().includes(term));
    }

    if (this.statusFilter !== 'ALL') {
      result = result.filter(ev => ev.statut === this.statusFilter);
    }

    if (this.dateFilter !== 'ALL') {
      const now = new Date();
      result = result.filter(ev => {
        const start = new Date(ev.dateDebut);
        const end = new Date(ev.dateFin);
        if (this.dateFilter === 'ACTIVE') return start <= now && end >= now;
        if (this.dateFilter === 'UPCOMING') return start > now;
        if (this.dateFilter === 'PAST') return end < now;
        return true;
      });
    }

    result.sort((a, b) => {
      const dir = this.sortDirection === 'asc' ? 1 : -1;
      const col = this.sortColumn;
      const valA = col.includes('date') ? new Date((a as any)[col]).getTime() : String((a as any)[col] ?? '').toLowerCase();
      const valB = col.includes('date') ? new Date((b as any)[col]).getTime() : String((b as any)[col] ?? '').toLowerCase();
      if (valA < valB) return -1 * dir;
      if (valA > valB) return 1 * dir;
      return 0;
    });

    return result;
  }

  get paginatedEvaluations(): Evaluation[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredEvaluations.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredEvaluations.length / this.pageSize));
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  loadEvaluations(): void {
    this.loading = true;
    this.evalService.getAll().subscribe({
      next: (data) => {
        this.evaluations = data;
        this.loading = false;
        this.currentPage = 1;
        this.animateCounts();
      },
      error: () => {
        this.toastr.error('Erreur de chargement des campagnes', 'Erreur');
        this.loading = false;
      }
    });
  }

  getCountByStatus(status: string): number {
    return this.evaluations.filter(ev => ev.statut === status).length;
  }

  getStatusLabel(statut: string | undefined): string {
    const labels: Record<string, string> = {
      BROUILLON: 'Brouillon', OUVERTE: 'Ouverte', FERMEE: 'Fermée', CLOTUREE: 'Clôturée'
    };
    return labels[statut || ''] || statut || 'Inconnu';
  }

  getStatusClass(statut: string | undefined): string {
    const classes: Record<string, string> = {
      BROUILLON: 'badge-campaign-draft', OUVERTE: 'badge-campaign-open',
      FERMEE: 'badge-campaign-closed', CLOTUREE: 'badge-campaign-done'
    };
    return classes[statut || ''] || 'badge-campaign-draft';
  }

  getInitials(nom: string | undefined): string {
    if (!nom) return '?';
    return nom.split(' ').filter(w => w.length > 0).slice(0, 2).map(w => w.charAt(0).toUpperCase()).join('');
  }

  getProgress(ev: Evaluation): number {
    const start = new Date(ev.dateDebut).getTime();
    const end = new Date(ev.dateFin).getTime();
    const now = Date.now();
    if (now <= start) return 0;
    if (now >= end) return 100;
    return Math.round(((now - start) / (end - start)) * 100);
  }

  sortBy(column: SortColumn): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) this.currentPage = page;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'ALL';
    this.dateFilter = 'ALL';
    this.currentPage = 1;
  }

  deleteEvaluation(ev: Evaluation): void {
    this.confirmService.confirm({
      title: 'Supprimer la campagne',
      message: `Voulez-vous vraiment supprimer la campagne « ${ev.nomEvaluation} » ? Cette action est irréversible.`,
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      icon: 'trash-2',
      confirmColor: 'danger',
      loadingText: 'Suppression...',
      successMessage: 'Campagne supprimée',
      errorMessage: 'Erreur de suppression',
      onConfirm: () => this.evalService.delete(ev.id!).pipe(tap(() => this.loadEvaluations()))
    }).subscribe();
  }

  ouvrir(id: number): void {
    this.evalService.ouvrir(id).subscribe({
      next: () => { this.toastr.success('Campagne ouverte', 'Succès'); this.loadEvaluations(); },
      error: (err) => this.toastr.error(err.error?.message || 'Erreur d\'ouverture', 'Erreur')
    });
  }

  fermer(id: number): void {
    this.evalService.fermer(id).subscribe({
      next: () => { this.toastr.success('Campagne fermée', 'Succès'); this.loadEvaluations(); },
      error: (err) => this.toastr.error(err.error?.message || 'Erreur de fermeture', 'Erreur')
    });
  }

  cloturer(id: number): void {
    this.evalService.cloturer(id).subscribe({
      next: () => { this.toastr.success('Campagne clôturée', 'Succès'); this.loadEvaluations(); },
      error: (err) => this.toastr.error(err.error?.message || 'Erreur de clôture', 'Erreur')
    });
  }

  private animateCounts(): void {
    const targets = {
      total: this.evaluations.length,
      draft: this.getCountByStatus('BROUILLON'),
      open: this.getCountByStatus('OUVERTE'),
      closed: this.getCountByStatus('FERMEE'),
      done: this.getCountByStatus('CLOTUREE')
    };
    const duration = 1200;
    const start = performance.now();

    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      this.displayCounts.total = Math.round(targets.total * eased);
      this.displayCounts.draft = Math.round(targets.draft * eased);
      this.displayCounts.open = Math.round(targets.open * eased);
      this.displayCounts.closed = Math.round(targets.closed * eased);
      this.displayCounts.done = Math.round(targets.done * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
}
