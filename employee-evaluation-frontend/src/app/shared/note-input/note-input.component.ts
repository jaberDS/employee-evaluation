import {
  AfterViewInit, Component, ElementRef, EventEmitter,
  Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild
} from '@angular/core';
import gsap from 'gsap';

/**
 * Saisie d'une note — champ réel, pas une rangée de boutons.
 *
 * Le responsable tape directement la valeur (décimales comprises : 7,25),
 * ou l'ajuste au pas près avec les boutons − / +. La jauge sous le champ
 * traduit visuellement le rapport note / barème.
 *
 * Deux variantes de présentation partagent la même mécanique :
 *  - `agence` : compacte, orientée saisie rapide sur un tableau de bord
 *  - `siege`  : plus large et posée, pour une lecture de type document
 */
@Component({
  selector: 'app-note-input',
  templateUrl: './note-input.component.html',
  styleUrls: ['./note-input.component.css']
})
export class NoteInputComponent implements AfterViewInit, OnChanges, OnDestroy {

  /** Valeur numérique retenue (null tant que rien n'est saisi). */
  @Input() value: number | null = null;
  /** Texte brut en cours de frappe — préserve « 7, » le temps de taper la décimale. */
  @Input() rawValue = '';
  @Input() noteMax = 10;
  @Input() variant: 'agence' | 'siege' = 'agence';
  @Input() label = '';
  @Input() invalid = false;

  /** Frappe au clavier : on remonte l'évènement brut, le conteneur nettoie la saisie. */
  @Output() rawInput = new EventEmitter<Event>();
  /** Ajustement par pas ou par la piste : valeur numérique déjà bornée. */
  @Output() valueChange = new EventEmitter<number>();

  @ViewChild('fill') fillRef?: ElementRef<HTMLElement>;
  @ViewChild('field') fieldRef?: ElementRef<HTMLInputElement>;

  private ctx?: gsap.Context;
  private lastPct = 0;

  // ─── Cycle de vie ─────────────────────────────────────────────────────────

  ngAfterViewInit(): void {
    this.ctx = gsap.context(() => {
      if (this.fillRef) {
        gsap.set(this.fillRef.nativeElement, { scaleX: this.pct / 100 });
      }
    });
    this.lastPct = this.pct;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] && !changes['value'].firstChange) {
      this.animateTo(this.pct);
    }
  }

  ngOnDestroy(): void {
    this.ctx?.revert();
  }

  /** La jauge glisse vers sa nouvelle longueur ; le champ tressaille brièvement. */
  private animateTo(pct: number): void {
    if (!this.fillRef) return;
    gsap.to(this.fillRef.nativeElement, {
      scaleX: pct / 100,
      duration: 0.45,
      ease: 'power3.out'
    });
    if (this.fieldRef && pct !== this.lastPct) {
      gsap.fromTo(this.fieldRef.nativeElement,
        { scale: 1.06 },
        { scale: 1, duration: 0.35, ease: 'back.out(2.4)' });
    }
    this.lastPct = pct;
  }

  // ─── État dérivé ──────────────────────────────────────────────────────────

  get pct(): number {
    if (this.value === null || this.value === undefined) return 0;
    const max = this.noteMax || 10;
    return Math.max(0, Math.min(100, (this.value / max) * 100));
  }

  get hasValue(): boolean {
    return this.value !== null && this.value !== undefined;
  }

  /** Palier d'appréciation — pilote la teinte de la jauge et la mention. */
  get tier(): 'faible' | 'moyen' | 'bon' | 'excellent' | 'vide' {
    if (!this.hasValue) return 'vide';
    const r = this.pct;
    if (r < 40) return 'faible';
    if (r < 60) return 'moyen';
    if (r < 80) return 'bon';
    return 'excellent';
  }

  get tierLabel(): string {
    const map = {
      vide: 'Non noté', faible: 'Insuffisant', moyen: 'À consolider',
      bon: 'Satisfaisant', excellent: 'Remarquable'
    };
    return map[this.tier];
  }

  /** Pas d'ajustement : entier sur les petits barèmes, demi-point au-delà. */
  get step(): number {
    return (this.noteMax || 10) > 20 ? 1 : 0.5;
  }

  get canDecrement(): boolean {
    return this.hasValue && (this.value as number) > 0;
  }

  get canIncrement(): boolean {
    return !this.hasValue || (this.value as number) < (this.noteMax || 10);
  }

  // ─── Interactions ─────────────────────────────────────────────────────────

  onInput(event: Event): void {
    this.rawInput.emit(event);
  }

  adjust(delta: number): void {
    const max = this.noteMax || 10;
    const base = this.hasValue ? (this.value as number) : 0;
    let next = base + delta;
    if (next < 0) next = 0;
    if (next > max) next = max;
    next = Math.round(next * 100) / 100;
    this.valueChange.emit(next);
  }

  /** Clic sur la piste : positionne la note au point cliqué. */
  onTrackClick(event: MouseEvent): void {
    const track = event.currentTarget as HTMLElement;
    const rect = track.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    const max = this.noteMax || 10;
    const stepped = Math.round((ratio * max) / this.step) * this.step;
    this.valueChange.emit(Math.round(stepped * 100) / 100);
  }

  /** Flèches haut/bas comme sur un champ numérique natif. */
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.adjust(this.step);
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.adjust(-this.step);
    }
  }
}
