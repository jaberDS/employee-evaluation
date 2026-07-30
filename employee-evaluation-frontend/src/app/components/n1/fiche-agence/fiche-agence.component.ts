import {
  AfterViewInit, ChangeDetectorRef, Component, ElementRef, EventEmitter,
  Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild
} from '@angular/core';
import { animate, query, stagger, style, transition, trigger } from '@angular/animations';
import gsap from 'gsap';
import { Evaluation, Question } from '../../../models/evaluation.model';
import { Employee } from '../../../models/employee.model';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

@Component({
  selector: 'app-fiche-agence',
  templateUrl: './fiche-agence.component.html',
  styleUrls: ['./fiche-agence.component.css'],
  animations: [
    // Les cartes se posent en cascade à l'arrivée du jeu de questions.
    trigger('gridStagger', [
      transition(':enter, * => *', [
        query('.ag-tile', [
          style({ opacity: 0, transform: 'translateY(18px) scale(0.985)' }),
          stagger(55, animate('420ms cubic-bezier(0.23, 1, 0.32, 1)',
            style({ opacity: 1, transform: 'none' })))
        ], { optional: true })
      ])
    ]),
    trigger('slideFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-6px)' }),
        animate('260ms ease-out', style({ opacity: 1, transform: 'none' }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0 }))
      ])
    ])
  ]
})
export class FicheAgenceComponent implements AfterViewInit, OnChanges, OnDestroy {

  @Input() questions: Question[] = [];
  @Input() answers: { [questionId: number]: any } = {};
  @Input() scoreInputs: { [questionId: number]: string } = {};
  @Input() employee: Employee | null = null;
  @Input() campaign: Evaluation | null = null;
  @Input() commentaire = '';
  @Input() progressPct = 0;
  @Input() answeredCount = 0;
  @Input() unansweredIds: number[] = [];
  @Input() submitting = false;
  @Input() autoSaveLabel = '';

  @Output() answerChange = new EventEmitter<{ questionId: number; value: any }>();
  @Output() scoreInput = new EventEmitter<{ questionId: number; event: Event; noteMax: number }>();
  @Output() commentaireChange = new EventEmitter<string>();
  @Output() submit = new EventEmitter<void>();
  @Output() saveDraft = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();

  @ViewChild('hero') heroRef?: ElementRef<HTMLElement>;

  /** Pourcentage affiché dans l'anneau — décompté par GSAP, décalé de `progressPct`. */
  displayPct = 0;

  private ctx?: gsap.Context;

  constructor(private cdr: ChangeDetectorRef) {}

  // ─── Anneau de progression ────────────────────────────────────────────────
  readonly ringRadius = 34;

  get ringCircumference(): number {
    return 2 * Math.PI * this.ringRadius;
  }

  get ringOffset(): number {
    return this.ringCircumference * (1 - this.displayPct / 100);
  }

  // ─── Mouvement ────────────────────────────────────────────────────────────

  ngAfterViewInit(): void {
    this.ctx = gsap.context(() => {
      if (this.heroRef) {
        const el = this.heroRef.nativeElement;
        gsap.from(el, {
          y: -22, opacity: 0, duration: 0.6, ease: 'power3.out'
        });
        gsap.from(el.querySelectorAll('.ag-avatar, .ag-hero-text > *, .ag-ring-wrap'), {
          y: 12, opacity: 0, duration: 0.5, stagger: 0.07, delay: 0.12, ease: 'power2.out'
        });
      }
      this.animateRing(this.progressPct);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['progressPct']) {
      if (changes['progressPct'].firstChange) {
        this.displayPct = 0;
      } else {
        this.animateRing(this.progressPct);
      }
    }
  }

  ngOnDestroy(): void {
    this.ctx?.revert();
  }

  /** Le compteur monte vers la valeur cible ; le tracé suit via `ringOffset`. */
  private animateRing(target: number): void {
    gsap.to(this, {
      displayPct: target,
      duration: 0.8,
      ease: 'power2.out',
      // GSAP mute la propriété hors du cycle Angular : on redessine à chaque frame.
      onUpdate: () => {
        this.displayPct = Math.round(this.displayPct);
        this.cdr.detectChanges();
      }
    });
  }

  // ─── Réponses ─────────────────────────────────────────────────────────────

  setAnswer(questionId: number, value: any): void {
    this.answerChange.emit({ questionId, value });
  }

  onScoreInput(questionId: number, event: Event, noteMax: number): void {
    this.scoreInput.emit({ questionId, event, noteMax });
  }

  getRatingValue(questionId: number): number | null {
    const v = this.answers[questionId];
    return v === undefined || v === '' ? null : v;
  }

  getTextValue(questionId: number): string {
    return this.answers[questionId] ?? '';
  }

  getOuiNonValue(questionId: number): boolean | null {
    return this.answers[questionId] ?? null;
  }

  isAnswered(q: Question): boolean {
    const v = this.answers[q.id!];
    return v !== undefined && v !== '';
  }

  isUnanswered(q: Question): boolean {
    return this.unansweredIds.includes(q.id!);
  }

  // ─── Affichage ────────────────────────────────────────────────────────────

  getTypeLabel(type: string): string {
    const map: Record<string, string> = {
      NOTE: 'Performance', OUI_NON: 'Conformité', TEXTE: 'Précision', COMMENTAIRE: 'Observation'
    };
    return map[type] ?? type;
  }

  getTypeIcon(type: string): IconName {
    const map: Record<string, IconName> = {
      NOTE: 'gauge', OUI_NON: 'toggle-left', TEXTE: 'type', COMMENTAIRE: 'message-square'
    };
    return map[type] ?? 'help-circle';
  }

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }
}
