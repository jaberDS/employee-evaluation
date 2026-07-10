import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { EmployeeService } from '../../../services/employee.service';

@Component({
  selector: 'app-employee-form',
  templateUrl: './employee-form.component.html',
  styleUrls: ['./employee-form.component.css']
})
export class EmployeeFormComponent implements OnInit {
  employeeForm: FormGroup;
  isEdit = false;
  employeeId?: number;

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
    if (this.employeeForm.invalid) return;
    const data = this.employeeForm.value;
    if (this.isEdit && this.employeeId) {
      this.employeeService.update(this.employeeId, data).subscribe({
        next: () => { this.toastr.success('Modifié'); this.router.navigate(['/employees']); },
        error: () => this.toastr.error('Erreur de modification')
      });
    } else {
      this.employeeService.create(data).subscribe({
        next: () => { this.toastr.success('Créé'); this.router.navigate(['/employees']); },
        error: () => this.toastr.error('Erreur de création')
      });
    }
  }
}
