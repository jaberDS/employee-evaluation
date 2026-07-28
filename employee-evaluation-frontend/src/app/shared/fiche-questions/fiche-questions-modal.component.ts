import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { EvaluationService } from '../../services/evaluation.service';
import { FicheService, FicheEvaluation } from '../../services/fiche.service';
import { Question } from '../../models/evaluation.model';
import { IconName } from '../lucide-icon/lucide-icon.component';

interface QuestionRow {
  question: Question;
  mark: number | null;
}

@Component({
  selector: 'app-fiche-questions-modal',
  templateUrl: './fiche-questions-modal.component.html',
  styleUrls: ['./fiche-questions-modal.component.css']
})
export class FicheQuestionsModalComponent implements OnChanges {

  /** Non-null ouvre la modale et déclenche le chargement. */
  @Input() ficheId: number | null = null;
  @Output() closed = new EventEmitter<void>();

  loading = false;
  error: string | null = null;
  fiche: FicheEvaluation | null = null;
  rows: QuestionRow[] = [];

  constructor(
    private ficheService: FicheService,
    private evaluationService: EvaluationService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['ficheId']) return;
    if (this.ficheId === null) {
      this.reset();
    } else {
      this.load(this.ficheId);
    }
  }

  private reset(): void {
    this.loading = false;
    this.error = null;
    this.fiche = null;
    this.rows = [];
  }

  private load(id: number): void {
    this.reset();
    this.loading = true;

    this.ficheService.getById(id).pipe(
      catchError(() => of(null))
    ).subscribe(fiche => {
      if (this.ficheId !== id) return;
      if (!fiche) {
        this.error = "Impossible de charger cette fiche d'évaluation.";
        this.loading = false;
        return;
      }

      this.fiche = fiche;
      this.evaluationService.getQuestions(fiche.evaluationId).pipe(
        catchError(() => of([] as Question[]))
      ).subscribe(questions => {
        if (this.ficheId !== id) return;
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

  close(): void {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.ficheId !== null) this.close();
  }

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

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }

  trackById(_: number, row: QuestionRow): number { return row.question.id!; }
}
