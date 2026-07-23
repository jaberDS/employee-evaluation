import { Component, OnInit } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { forkJoin, of } from 'rxjs';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../../services/auth.service';
import { EvaluationService } from '../../../services/evaluation.service';
import { FicheService, FicheEvaluation } from '../../../services/fiche.service';
import { Evaluation } from '../../../models/evaluation.model';

interface StatutMeta {
  label: string;
  cssClass: string;
}

const STATUT_META: Record<string, StatutMeta> = {
  EN_ATTENTE:         { label: 'En attente', cssClass: 'fl-badge-neutral' },
  EN_COURS_N1:        { label: 'En cours (N+1)', cssClass: 'fl-badge-neutral' },
  EN_ATTENTE_N2:       { label: 'En validation (N+2)', cssClass: 'fl-badge-info' },
  EN_ATTENTE_EMPLOYE: { label: 'Action requise', cssClass: 'fl-badge-accent' },
  A_REVISER:          { label: 'En révision', cssClass: 'fl-badge-warning' },
  CLOTUREE:           { label: 'Clôturée', cssClass: 'fl-badge-success' }
};

@Component({
  selector: 'app-fiche-list',
  templateUrl: './fiche-list.component.html',
  styleUrls: ['./fiche-list.component.css']
})
export class FicheListComponent implements OnInit {

  loading = true;
  fiches: FicheEvaluation[] = [];

  constructor(
    private authService: AuthService,
    private evaluationService: EvaluationService,
    private ficheService: FicheService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    const user = this.authService.getUser();
    if (!user?.id) {
      this.loading = false;
      return;
    }
    forkJoin({
      fiches: this.ficheService.getByEmploye(user.id).pipe(catchError(() => of([] as FicheEvaluation[]))),
      evaluations: this.evaluationService.getAll().pipe(catchError(() => of([] as Evaluation[])))
    }).subscribe({
      next: ({ fiches, evaluations }) => {
        const openIds = new Set(evaluations.filter(e => e.statut === 'OUVERTE').map(e => e.id));
        this.fiches = fiches
          .filter(f => openIds.has(f.evaluationId))
          .sort((a, b) => new Date(b.dateCreation).getTime() - new Date(a.dateCreation).getTime());
        this.loading = false;
      },
      error: () => {
        this.toastr.error('Erreur de chargement des évaluations', 'Erreur');
        this.loading = false;
      }
    });
  }

  statutMeta(statut: string): StatutMeta {
    return STATUT_META[statut] ?? { label: statut, cssClass: 'fl-badge-neutral' };
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

  trackById(_: number, item: FicheEvaluation): number { return item.id; }
}
