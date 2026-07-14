import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { tap } from 'rxjs';
import { EmployeeService } from '../../../services/employee.service';
import { Employee } from '../../../models/employee.model';
import { ConfirmService } from '../../../shared/confirm/confirm.service';

type SortColumn = 'matricule' | 'nom' | 'prenom' | 'email' | 'role' | 'actif';
type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-employee-list',
  templateUrl: './employee-list.component.html',
  styleUrls: ['./employee-list.component.css']
})
export class EmployeeListComponent implements OnInit, AfterViewInit {
  employees: Employee[] = [];
  loading = true;
  searchTerm = '';
  roleFilter = 'ALL';
  statusFilter = 'ALL';
  sortColumn: SortColumn = 'nom';
  sortDirection: SortDirection = 'asc';
  currentPage = 1;
  pageSize = 8;

  displayCounts = { total: 0, active: 0, inactive: 0, admin: 0 };

  constructor(
    private employeeService: EmployeeService,
    private toastr: ToastrService,
    private confirmService: ConfirmService
  ) {}

  ngOnInit(): void {
    this.loadEmployees();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.animateCounts(), 200);
  }

  get filteredEmployees(): Employee[] {
    let result = [...this.employees];

    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase().trim();
      result = result.filter(emp =>
        emp.matricule.toLowerCase().includes(term) ||
        emp.nom.toLowerCase().includes(term) ||
        emp.prenom.toLowerCase().includes(term) ||
        emp.email.toLowerCase().includes(term)
      );
    }

    if (this.roleFilter !== 'ALL') {
      result = result.filter(emp => emp.role === this.roleFilter);
    }

    if (this.statusFilter === 'ACTIVE') {
      result = result.filter(emp => emp.actif);
    } else if (this.statusFilter === 'INACTIVE') {
      result = result.filter(emp => !emp.actif);
    }

    result.sort((a, b) => {
      const dir = this.sortDirection === 'asc' ? 1 : -1;
      const col = this.sortColumn;
      const valA = col === 'actif' ? (a.actif ? 1 : 0) : String((a as any)[col] ?? '').toLowerCase();
      const valB = col === 'actif' ? (b.actif ? 1 : 0) : String((b as any)[col] ?? '').toLowerCase();
      if (valA < valB) return -1 * dir;
      if (valA > valB) return 1 * dir;
      return 0;
    });

    return result;
  }

  get paginatedEmployees(): Employee[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredEmployees.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredEmployees.length / this.pageSize));
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  loadEmployees(): void {
    this.loading = true;
    this.employeeService.getAll().subscribe({
      next: (data) => {
        this.employees = data;
        this.loading = false;
        this.currentPage = 1;
        this.animateCounts();
      },
      error: () => {
        this.toastr.error('Erreur lors du chargement des employés', 'Erreur');
        this.loading = false;
      }
    });
  }

  deleteEmployee(emp: Employee): void {
    this.confirmService.confirm({
      title: 'Supprimer l\'employé',
      message: `Voulez-vous vraiment supprimer ${emp.prenom} ${emp.nom} ? Cette action est irréversible.`,
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      icon: 'trash-2',
      confirmColor: 'danger',
      loadingText: 'Suppression...',
      successMessage: 'Employé supprimé avec succès',
      errorMessage: 'Erreur lors de la suppression',
      onConfirm: () => this.employeeService.delete(emp.id!).pipe(tap(() => this.loadEmployees()))
    }).subscribe();
  }

  sortBy(column: SortColumn): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
    }
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.roleFilter = 'ALL';
    this.statusFilter = 'ALL';
    this.currentPage = 1;
  }

  getInitials(emp: Employee): string {
    return ((emp.prenom?.charAt(0) || '') + (emp.nom?.charAt(0) || '')).toUpperCase();
  }

  getRoleIcon(role: string): 'crown' | 'activity' | 'bar-chart-3' | 'user' {
    const icons: Record<string, 'crown' | 'activity' | 'bar-chart-3' | 'user'> = {
      ADMIN: 'crown', N1: 'activity', N2: 'bar-chart-3', EMPLOYE: 'user'
    };
    return icons[role] || 'user';
  }

  getRoleClass(role: string): string {
    const classes: Record<string, string> = {
      ADMIN: 'badge-role-admin', N1: 'badge-role-n1', N2: 'badge-role-n2', EMPLOYE: 'badge-role-employee'
    };
    return classes[role] || 'badge-role-employee';
  }

  private animateCounts(): void {
    const targets = {
      total: this.employees.length,
      active: this.employees.filter(e => e.actif).length,
      inactive: this.employees.filter(e => !e.actif).length,
      admin: this.employees.filter(e => e.role === 'ADMIN').length
    };
    const duration = 1200;
    const start = performance.now();

    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      this.displayCounts.total = Math.round(targets.total * eased);
      this.displayCounts.active = Math.round(targets.active * eased);
      this.displayCounts.inactive = Math.round(targets.inactive * eased);
      this.displayCounts.admin = Math.round(targets.admin * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
}
