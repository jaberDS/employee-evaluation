import {
  AfterViewInit, Component, ElementRef, EventEmitter,
  Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild
} from '@angular/core';
import { animate, query, stagger, style, transition, trigger } from '@angular/animations';
import gsap from 'gsap';
import { Evaluation, Question } from '../../../models/evaluation.model';
import { Employee } from '../../../models/employee.model';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

@Component({
  selector: 'app-fiche-siege',
  templateUrl: './fiche-siege.component.html',
  styleUrls: ['./fiche-siege.component.css'],
  animations: [
    // Registre documentaire : les articles se dévoilent par le côté, sans rebond.
    trigger('articlesStagger', [
      transition(':enter, * => *', [
        query('.sg-article', [
          style({ opacity: 0, transform: 'translateX(-10px)' }),
          stagger(45, animate('380ms cubic-bezier(0.16, 1, 0.3, 1)',
            style({ opacity: 1, transform: 'none' })))
        ], { optional: true })
      ])
    ]),
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('240ms ease-out', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('160ms ease-in', style({ opacity: 0 }))
      ])
    ])
  ]
})
export class FicheSiegeComponent implements AfterViewInit, OnChanges, OnDestroy {

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

  @ViewChild('letterhead') letterheadRef?: ElementRef<HTMLElement>;
  @ViewChild('rule') ruleRef?: ElementRef<HTMLElement>;
  @ViewChild('progressFill') progressFillRef?: ElementRef<HTMLElement>;

  private ctx?: gsap.Context;

  // ─── Mouvement ────────────────────────────────────────────────────────────

  ngAfterViewInit(): void {
    this.ctx = gsap.context(() => {
      // Le filet se trace de gauche à droite, comme un tampon apposé sur le dossier.
      if (this.ruleRef) {
        gsap.from(this.ruleRef.nativeElement, {
          scaleX: 0, transformOrigin: 'left center', duration: 0.75, ease: 'power3.inOut'
        });
      }
      if (this.letterheadRef) {
        gsap.from(
          this.letterheadRef.nativeElement.querySelectorAll('.sg-title, .sg-meta-item, .sg-progress'),
          { y: 10, opacity: 0, duration: 0.45, stagger: 0.05, delay: 0.15, ease: 'power2.out' }
        );
      }
      this.animateProgress(this.progressPct);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['progressPct'] && !changes['progressPct'].firstChange) {
      this.animateProgress(this.progressPct);
    }
  }

  ngOnDestroy(): void {
    this.ctx?.revert();
  }

  private animateProgress(target: number): void {
    if (!this.progressFillRef) return;
    gsap.to(this.progressFillRef.nativeElement, {
      width: `${target}%`, duration: 0.65, ease: 'power2.out'
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

  // ─── Sommaire ─────────────────────────────────────────────────────────────

  scrollToQuestion(questionId: number): void {
    document.getElementById(`q-${questionId}`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // ─── Affichage ────────────────────────────────────────────────────────────

  getTypeLabel(type: string): string {
    const map: Record<string, string> = {
      NOTE: 'Notation', OUI_NON: 'Appréciation binaire', TEXTE: 'Mention', COMMENTAIRE: 'Développement'
    };
    return map[type] ?? type;
  }

  getTypeIcon(type: string): IconName {
    const map: Record<string, IconName> = {
      NOTE: 'bar-chart-3', OUI_NON: 'toggle-left', TEXTE: 'type', COMMENTAIRE: 'align-left'
    };
    return map[type] ?? 'help-circle';
  }

  getInitials(prenom: string, nom: string): string {
    return ((prenom?.charAt(0) ?? '') + (nom?.charAt(0) ?? '')).toUpperCase();
  }
}
