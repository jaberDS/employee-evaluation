import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { EvaluationService } from '../../../services/evaluation.service';

@Component({
  selector: 'app-evaluation-form',
  templateUrl: './evaluation-form.component.html',
  styleUrls: ['./evaluation-form.component.css']
})
export class EvaluationFormComponent implements OnInit {
  evaluationForm: FormGroup;
  isEdit = false;
  evaluationId?: number;
  submitted = false;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private evalService: EvaluationService,
    private route: ActivatedRoute,
    private router: Router,
    private toastr: ToastrService
  ) {
    this.evaluationForm = this.fb.group({
      nomEvaluation: ['', [Validators.required, Validators.maxLength(100)]],
      dateDebut: ['', Validators.required],
      dateFin: ['', Validators.required]
    });
  }

  ngOnInit() {
    this.route.params.subscribe(params => {
      if (params['id']) {
        this.isEdit = true;
        this.evaluationId = +params['id'];
        this.loadEvaluation();
      }
    });
  }

  get f() { return this.evaluationForm.controls; }

  loadEvaluation() {
    if (this.evaluationId) {
      this.evalService.getById(this.evaluationId).subscribe({
        next: (data) => {
          this.evaluationForm.patchValue({
            nomEvaluation: data.nomEvaluation,
            dateDebut: data.dateDebut.replace('Z', ''), // pour datetime-local
            dateFin: data.dateFin.replace('Z', '')
          });
        },
        error: () => {
          this.toastr.error('Erreur lors du chargement de la campagne', 'Erreur');
          this.router.navigate(['/evaluations']);
        }
      });
    }
  }

  onSubmit() {
    this.submitted = true;
    if (this.evaluationForm.invalid) {
      this.toastr.warning('Veuillez corriger les erreurs', 'Attention');
      return;
    }

    this.loading = true;
    const data = this.evaluationForm.value;

    if (this.isEdit && this.evaluationId) {
      this.evalService.update(this.evaluationId, data).subscribe({
        next: () => {
          this.toastr.success('Campagne modifiée avec succès', 'Succès');
          this.router.navigate(['/evaluations']);
        },
        error: (err) => {
          this.toastr.error(err.error?.message || 'Erreur de modification', 'Erreur');
          this.loading = false;
        }
      });
    } else {
      this.evalService.create(data).subscribe({
        next: (res) => {
          this.toastr.success('Campagne créée avec succès', 'Succès');
          // Rediriger vers la liste des questions de cette campagne
          this.router.navigate(['/evaluations', res.id, 'questions']);
        },
        error: (err) => {
          this.toastr.error(err.error?.message || 'Erreur de création', 'Erreur');
          this.loading = false;
        }
      });
    }
  }
}
