import { Component, OnInit, AfterViewInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { tap } from 'rxjs';
import { EvaluationService } from '../../../services/evaluation.service';
import { Question, TypeQuestion } from '../../../models/evaluation.model';
import { Evaluation } from '../../../models/evaluation.model';
import { ConfirmService } from '../../../shared/confirm/confirm.service';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

type SortColumn = 'ordre' | 'libelle' | 'typeQuestion' | 'noteMax' | 'obligatoire' | 'actif';
type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-question-list',
  templateUrl: './question-list.component.html',
  styleUrls: ['./question-list.component.css']
})
export class QuestionListComponent implements OnInit, AfterViewInit {

  evaluationId!: number;
  evaluation: Evaluation | null = null;
  questions: Question[] = [];
  loading = true;

  searchTerm = '';
  typeFilter: TypeQuestion | 'ALL' = 'ALL';
  statusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';

  sortColumn: SortColumn = 'ordre';
  sortDirection: SortDirection = 'asc';

  currentPage = 1;
  pageSize = 8;

  readonly MAX_QUESTIONS = 10;

  displayCounts = { total: 0, active: 0, inactive: 0, required: 0 };

  // Detail modal
  selectedQuestion: Question | null = null;
  showDetailModal = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private evaluationService: EvaluationService,
    private toastr: ToastrService,
    private confirmService: ConfirmService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.evaluationId = +params['id'];
      this.loadData();
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.animateCounts(), 300);
  }

  // ─── Data loading ────────────────────────────────────────────────────────────

  loadData(): void {
    this.loading = true;
    this.evaluationService.getById(this.evaluationId).subscribe({
      next: (ev) => { this.evaluation = ev; },
      error: () => this.toastr.error('Campagne introuvable', 'Erreur')
    });
    this.evaluationService.getQuestions(this.evaluationId).subscribe({
      next: (data) => {
        this.questions = data;
        this.loading = false;
        this.currentPage = 1;
        this.animateCounts();
      },
      error: () => {
        this.toastr.error('Erreur lors du chargement des questions', 'Erreur');
        this.loading = false;
      }
    });
  }

  // ─── Filtering & sorting ─────────────────────────────────────────────────────

  get filteredQuestions(): Question[] {
    let result = [...this.questions];

    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase().trim();
      result = result.filter(q =>
        q.libelle.toLowerCase().includes(term) ||
        (q.description ?? '').toLowerCase().includes(term)
      );
    }

    if (this.typeFilter !== 'ALL') {
      result = result.filter(q => q.typeQuestion === this.typeFilter);
    }

    if (this.statusFilter === 'ACTIVE') {
      result = result.filter(q => q.actif);
    } else if (this.statusFilter === 'INACTIVE') {
      result = result.filter(q => !q.actif);
    }

    result.sort((a, b) => {
      const dir = this.sortDirection === 'asc' ? 1 : -1;
      const col = this.sortColumn;
      const valA = col === 'actif' || col === 'obligatoire'
        ? ((a as any)[col] ? 1 : 0)
        : String((a as any)[col] ?? '').toLowerCase();
      const valB = col === 'actif' || col === 'obligatoire'
        ? ((b as any)[col] ? 1 : 0)
        : String((b as any)[col] ?? '').toLowerCase();
      if (valA < valB) return -1 * dir;
      if (valA > valB) return 1 * dir;
      return 0;
    });

    return result;
  }

  get paginatedQuestions(): Question[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredQuestions.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredQuestions.length / this.pageSize));
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  /** Returns 0–100 fill % for the 10-question capacity bar */
  get capacityPercent(): number {
    return Math.round((this.questions.length / this.MAX_QUESTIONS) * 100);
  }

  get capacityFull(): boolean {
    return this.questions.length >= this.MAX_QUESTIONS;
  }

  // ─── Actions ─────────────────────────────────────────────────────────────────

  deleteQuestion(q: Question): void {
    this.confirmService.confirm({
      title: 'Supprimer la question',
      message: `Voulez-vous vraiment supprimer « ${q.libelle} » ? Cette action est irréversible.`,
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      icon: 'trash-2',
      confirmColor: 'danger',
      loadingText: 'Suppression...',
      successMessage: 'Question supprimée avec succès',
      errorMessage: 'Erreur lors de la suppression',
      onConfirm: () => this.evaluationService.deleteQuestion(q.id!).pipe(tap(() => this.loadData()))
    }).subscribe();
  }

  toggleActif(q: Question): void {
    this.evaluationService.toggleActif(q.id!).subscribe({
      next: (updated) => {
        const idx = this.questions.findIndex(x => x.id === q.id);
        if (idx !== -1) { this.questions[idx] = updated; }
        this.animateCounts();
        this.toastr.success(
          updated.actif ? 'Question activée' : 'Question désactivée',
          'Succès'
        );
      },
      error: () => this.toastr.error('Erreur lors du changement de statut', 'Erreur')
    });
  }

  openDetail(q: Question): void {
    this.selectedQuestion = q;
    this.showDetailModal = true;
  }

  closeDetail(): void {
    this.showDetailModal = false;
    this.selectedQuestion = null;
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
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
    }
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.typeFilter = 'ALL';
    this.statusFilter = 'ALL';
    this.currentPage = 1;
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  getTypeLabel(type: TypeQuestion): string {
    const labels: Record<TypeQuestion, string> = {
      NOTE: 'Note 1-10',
      OUI_NON: 'Oui / Non',
      TEXTE: 'Texte libre',
      COMMENTAIRE: 'Commentaire'
    };
    return labels[type] ?? type;
  }

  getTypeIcon(type: TypeQuestion): IconName {
    const icons: Record<TypeQuestion, IconName> = {
      NOTE: 'star',
      OUI_NON: 'toggle-left',
      TEXTE: 'type',
      COMMENTAIRE: 'message-square'
    };
    return icons[type] ?? 'help-circle';
  }

  getTypeClass(type: TypeQuestion): string {
    const classes: Record<TypeQuestion, string> = {
      NOTE: 'badge-type-note',
      OUI_NON: 'badge-type-oui-non',
      TEXTE: 'badge-type-texte',
      COMMENTAIRE: 'badge-type-commentaire'
    };
    return classes[type] ?? '';
  }

  getOrdreLabel(ordre: number): string {
    return ordre < 10 ? `0${ordre}` : `${ordre}`;
  }

  formatDate(d?: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  private animateCounts(): void {
    const targets = {
      total: this.questions.length,
      active: this.questions.filter(q => q.actif).length,
      inactive: this.questions.filter(q => !q.actif).length,
      required: this.questions.filter(q => q.obligatoire).length
    };
    const duration = 900;
    const start = performance.now();
    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      this.displayCounts.total    = Math.round(targets.total    * eased);
      this.displayCounts.active   = Math.round(targets.active   * eased);
      this.displayCounts.inactive = Math.round(targets.inactive * eased);
      this.displayCounts.required = Math.round(targets.required * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
}
