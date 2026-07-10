import { Component, OnInit } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { EmployeeService } from '../../../services/employee.service';
import { Employee } from '../../../models/employee.model';

@Component({
  selector: 'app-employee-list',
  templateUrl: './employee-list.component.html',
  styleUrls: ['./employee-list.component.css']
})
export class EmployeeListComponent implements OnInit {
  employees: Employee[] = [];
  loading = true;
  searchTerm: string = '';

  constructor(
    private employeeService: EmployeeService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    this.loadEmployees();
  }

  get filteredEmployees(): Employee[] {
    if (!this.searchTerm || this.searchTerm.trim() === '') {
      return this.employees;
    }
    const term = this.searchTerm.toLowerCase().trim();
    return this.employees.filter(emp =>
      emp.matricule.toLowerCase().includes(term) ||
      emp.nom.toLowerCase().includes(term) ||
      emp.prenom.toLowerCase().includes(term) ||
      emp.email.toLowerCase().includes(term)
    );
  }

  loadEmployees() {
    this.loading = true;
    this.employeeService.getAll().subscribe({
      next: (data) => {
        this.employees = data;
        this.loading = false;
      },
      error: () => {
        this.toastr.error('Erreur lors du chargement des employés', 'Erreur');
        this.loading = false;
      }
    });
  }

  deleteEmployee(id: number) {
    if (confirm('Voulez-vous vraiment supprimer cet employé ?')) {
      this.employeeService.delete(id).subscribe({
        next: () => {
          this.toastr.success('Employé supprimé avec succès', 'Succès');
          this.loadEmployees();
        },
        error: () => {
          this.toastr.error('Erreur lors de la suppression', 'Erreur');
        }
      });
    }
  }

  // ===== STATISTIQUES =====
  getActiveCount(): number {
    return this.employees.filter(e => e.actif).length;
  }

  getInactiveCount(): number {
    return this.employees.filter(e => !e.actif).length;
  }

  getAdminCount(): number {
    return this.employees.filter(e => e.role === 'ADMIN').length;
  }

  // ===== STYLES DES BADGES =====
  getRoleClass(role: string): string {
    const classes: { [key: string]: string } = {
      'ADMIN': 'role-admin',
      'N1': 'role-n1',
      'N2': 'role-n2',
      'EMPLOYE': 'role-employee'
    };
    return classes[role] || 'role-employee';
  }

  getRoleIcon(role: string): string {
    const icons: { [key: string]: string } = {
      'ADMIN': 'fa-crown',
      'N1': 'fa-chart-line',
      'N2': 'fa-chart-bar',
      'EMPLOYE': 'fa-user'
    };
    return icons[role] || 'fa-user';
  }
}
