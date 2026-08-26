import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { trigger, transition, style, animate } from '@angular/animations';
import { EvaluationService } from '../../../services/evaluation.service';
import { Evaluation } from '../../../models/evaluation.model';
import { IconName } from '../../../shared/lucide-icon/lucide-icon.component';

@Component({
  selector: 'app-question-form',
  templateUrl: './question-form.component.html',
  styleUrls: ['./question-form.component.css'],
  animations: [
    trigger('fadeSlideIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(24px)' }),
        animate('500ms cubic-bezier(0.23, 1, 0.32, 1)', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ])
  ]
})
export class QuestionFormComponent implements OnInit {
  questionForm: FormGroup;
  isEdit = false;
  questionId?: number;
  evaluationId?: number;
  evaluation: Evaluation | null = null;
  submitted = false;
  loading = false;
  showNoteMax = true;

  readonly typeOptions: { value: string; label: string; icon: IconName; desc: string }[] = [
    { value: 'NOTE',        label: 'Note 1-10',    icon: 'star',           desc: 'Évaluation numérique de 1 à 10' },
    { value: 'OUI_NON',     label: 'Oui / Non',    icon: 'toggle-left',    desc: 'Réponse binaire Oui ou Non' },
    { value: 'TEXTE',       label: 'Texte libre',  icon: 'type',           desc: 'Saisie libre d\'une ligne' },
    { value: 'COMMENTAIRE', label: 'Commentaire',  icon: 'message-square', desc: 'Zone de texte multi-lignes' }
  ];

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private evaluationService: EvaluationService,
    private toastr: ToastrService
  ) {
    this.questionForm = this.fb.group({
      libelle:      ['', [Validators.required, Validators.maxLength(500)]],
      description:  ['', Validators.maxLength(1000)],
      typeQuestion: ['NOTE', Validators.required],
      noteMax:      [10, [Validators.required, Validators.min(1), Validators.max(10)]],
      ordre:        [null, [Validators.required, Validators.min(1), Validators.max(10)]],
      obligatoire:  [true, Validators.required],
      actif:        [true]
    });
  }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      if (params['id']) {
        // Edit mode: /questions/edit/:id
        this.isEdit = true;
        this.questionId = +params['id'];
        this.loadQuestion();
      } else if (params['evaluationId']) {
        // Create mode: /questions/create/:evaluationId
        this.evaluationId = +params['evaluationId'];
        this.loadEvaluation(this.evaluationId);
      }
    });

    // Toggle noteMax visibility based on type
    this.questionForm.get('typeQuestion')!.valueChanges.subscribe(type => {
      this.showNoteMax = type === 'NOTE';
      if (type !== 'NOTE') {
        this.questionForm.get('noteMax')!.clearValidators();
        this.questionForm.get('noteMax')!.setValue(null);
      } else {
        this.questionForm.get('noteMax')!.setValidators([Validators.required, Validators.min(1), Validators.max(10)]);
        this.questionForm.get('noteMax')!.setValue(10);
      }
      this.questionForm.get('noteMax')!.updateValueAndValidity();
    });
  }

  private loadQuestion(): void {
    this.loading = true;
    this.evaluationService.getQuestionById(this.questionId!).subscribe({
      next: (q) => {
        this.evaluationId = q.evaluationId;
        this.showNoteMax = q.typeQuestion === 'NOTE';
        this.questionForm.patchValue({
          libelle:      q.libelle,
          description:  q.description ?? '',
          typeQuestion: q.typeQuestion,
          noteMax:      q.noteMax,
          ordre:        q.ordre,
          obligatoire:  q.obligatoire,
          actif:        q.actif ?? true
        });
        if (this.evaluationId) {
          this.loadEvaluation(this.evaluationId);
        }
        this.loading = false;
      },
      error: () => {
        this.toastr.error('Question introuvable', 'Erreur');
        this.loading = false;
        this.router.navigate(['/evaluations']);
      }
    });
  }

  private loadEvaluation(id: number): void {
    this.evaluationService.getById(id).subscribe({
      next: (ev) => { this.evaluation = ev; },
      error: () => {}
    });
  }

  onSubmit(): void {
    this.submitted = true;
    if (this.questionForm.invalid) {
      this.toastr.warning('Veuillez corriger les erreurs', 'Attention');
      return;
    }

    this.loading = true;
    const data = this.questionForm.value;

    if (this.isEdit && this.questionId) {
      this.evaluationService.updateQuestion(this.questionId, data).subscribe({
        next: () => {
          this.toastr.success('Question modifiée avec succès', 'Succès');
          this.router.navigate(['/evaluations', this.evaluationId, 'questions']);
        },
        error: (err) => {
          const msg = err?.error?.message ?? 'Erreur lors de la modification';
          this.toastr.error(msg, 'Erreur');
          this.loading = false;
        }
      });
    } else {
      this.evaluationService.addQuestion(this.evaluationId!, data).subscribe({
        next: () => {
          this.toastr.success('Question créée avec succès', 'Succès');
          this.router.navigate(['/evaluations', this.evaluationId, 'questions']);
        },
        error: (err) => {
          const msg = err?.error?.message ?? 'Erreur lors de la création';
          this.toastr.error(msg, 'Erreur');
          this.loading = false;
        }
      });
    }
  }

  get backUrl(): (string | number)[] {
    return this.evaluationId ? ['/evaluations', this.evaluationId, 'questions'] : ['/evaluations'];
  }

  createRipple(event: MouseEvent): void {
    const button = event.currentTarget as HTMLElement;
    const rect = button.getBoundingClientRect();
    const ripple = document.createElement('span');
    const size = Math.max(rect.width, rect.height);
    ripple.style.width = ripple.style.height = `${size}px`;
    ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
    ripple.style.top  = `${event.clientY - rect.top  - size / 2}px`;
    ripple.classList.add('emp-ripple');
    button.appendChild(ripple);
    setTimeout(() => ripple.remove(), 700);
  }
}
