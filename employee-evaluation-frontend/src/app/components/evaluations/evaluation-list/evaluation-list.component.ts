import { Component, OnInit } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { EvaluationService } from '../../../services/evaluation.service';
import { Evaluation } from '../../../models/evaluation.model';

@Component({
  selector: 'app-evaluation-list',
  templateUrl: './evaluation-list.component.html',
  styleUrls: ['./evaluation-list.component.css']
})
export class EvaluationListComponent implements OnInit {
  evaluations: Evaluation[] = [];
  loading = true;
  searchTerm = '';

  constructor(
    private evalService: EvaluationService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    this.loadEvaluations();
  }

  get filteredEvaluations(): Evaluation[] {
    if (!this.searchTerm || this.searchTerm.trim() === '') {
      return this.evaluations;
    }
    const term = this.searchTerm.toLowerCase().trim();
    return this.evaluations.filter(ev =>
      ev.nomEvaluation.toLowerCase().includes(term)
    );
  }

  loadEvaluations() {
    this.loading = true;
    this.evalService.getAll().subscribe({
      next: (data) => {
        this.evaluations = data;
        this.loading = false;
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

  // ===== HELPERS D'AFFICHAGE =====
  getStatusIcon(statut: string | undefined): string {
    switch (statut) {
      case 'BROUILLON': return 'fa-pen';
      case 'OUVERTE': return 'fa-play';
      case 'FERMEE': return 'fa-pause';
      case 'CLOTUREE': return 'fa-check-circle';
      default: return 'fa-circle';
    }
  }

  getStatusLabel(statut: string | undefined): string {
    switch (statut) {
      case 'BROUILLON': return 'Brouillon';
      case 'OUVERTE': return 'Ouverte';
      case 'FERMEE': return 'Fermée';
      case 'CLOTUREE': return 'Clôturée';
      default: return statut || 'Inconnu';
    }
  }

  getInitials(nom: string | undefined): string {
    if (!nom) return '?';
    return nom
      .split(' ')
      .filter(w => w.length > 0)
      .slice(0, 2)
      .map(w => w.charAt(0).toUpperCase())
      .join('');
  }

  // ===== ACTIONS =====
  deleteEvaluation(id: number) {
    if (confirm('Voulez-vous vraiment supprimer cette campagne ?')) {
      this.evalService.delete(id).subscribe({
        next: () => {
          this.toastr.success('Campagne supprimée', 'Succès');
          this.loadEvaluations();
        },
        error: () => this.toastr.error('Erreur de suppression', 'Erreur')
      });
    }
  }

  ouvrir(id: number) {
    this.evalService.ouvrir(id).subscribe({
      next: () => {
        this.toastr.success('Campagne ouverte', 'Succès');
        this.loadEvaluations();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Erreur d\'ouverture', 'Erreur')
    });
  }

  fermer(id: number) {
    this.evalService.fermer(id).subscribe({
      next: () => {
        this.toastr.success('Campagne fermée', 'Succès');
        this.loadEvaluations();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Erreur de fermeture', 'Erreur')
    });
  }

  cloturer(id: number) {
    this.evalService.cloturer(id).subscribe({
      next: () => {
        this.toastr.success('Campagne clôturée', 'Succès');
        this.loadEvaluations();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Erreur de clôture', 'Erreur')
    });
  }
}
