import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { EmployeeService } from '../../../services/employee.service';
import { trigger, transition, style, animate, query, stagger } from '@angular/animations';

@Component({
  selector: 'app-employee-form',
  templateUrl: './employee-form.component.html',
  styleUrls: ['./employee-form.component.css'],
  animations: [
    trigger('fadeSlideIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(24px)' }),
        animate('500ms cubic-bezier(0.23, 1, 0.32, 1)', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ])
  ]
})
export class EmployeeFormComponent implements OnInit {
  employeeForm: FormGroup;
  isEdit = false;
  employeeId?: number;
  submitted = false;
  loading = false;
  showPassword = false;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private employeeService: EmployeeService,
    private toastr: ToastrService
  ) {
    this.employeeForm = this.fb.group({
      matricule: ['', Validators.required],
      nom: ['', Validators.required],
      prenom: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      motDePasse: ['', this.isEdit ? [] : [Validators.required, Validators.minLength(4)]],
      role: ['EMPLOYE', Validators.required],
      actif: [true]
    });
  }

  ngOnInit() {
    this.route.params.subscribe(params => {
      if (params['id']) {
        this.isEdit = true;
        this.employeeId = +params['id'];
        this.employeeService.getById(this.employeeId).subscribe({
          next: data => this.employeeForm.patchValue(data),
          error: () => this.toastr.error('Erreur de chargement')
        });
      }
    });
  }

  onSubmit() {
    this.submitted = true;
    if (this.employeeForm.invalid) {
      this.toastr.warning('Veuillez corriger les erreurs', 'Attention');
      return;
    }

    this.loading = true;
    const data = this.employeeForm.value;

    if (this.isEdit && this.employeeId) {
      this.employeeService.update(this.employeeId, data).subscribe({
        next: () => { this.toastr.success('Employé modifié avec succès', 'Succès'); this.router.navigate(['/employees']); },
        error: () => { this.toastr.error('Erreur de modification'); this.loading = false; }
      });
    } else {
      this.employeeService.create(data).subscribe({
        next: () => { this.toastr.success('Employé créé avec succès', 'Succès'); this.router.navigate(['/employees']); },
        error: () => { this.toastr.error('Erreur de création'); this.loading = false; }
      });
    }
  }

  createRipple(event: MouseEvent) {
    const button = event.currentTarget as HTMLElement;
    const rect = button.getBoundingClientRect();
    const ripple = document.createElement('span');
    const size = Math.max(rect.width, rect.height);
    ripple.style.width = ripple.style.height = `${size}px`;
    ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
    ripple.style.top = `${event.clientY - rect.top - size / 2}px`;
    ripple.classList.add('emp-ripple');
    button.appendChild(ripple);
    setTimeout(() => ripple.remove(), 700);
  }
}
