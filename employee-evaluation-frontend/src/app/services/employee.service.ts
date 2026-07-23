import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Employee {
  id?: number;
  matricule: string;
  nom: string;
  prenom: string;
  email: string;
  motDePasse?: string;
  role: string;
  n1Id?: number | null;
  n1Nom?: string | null;
  n1Prenom?: string | null;
  n2Id?: number | null;
  n2Nom?: string | null;
  n2Prenom?: string | null;
  actif?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class EmployeeService {
  private apiUrl = `${environment.apiUrl}/employes`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.apiUrl);
  }

  getById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.apiUrl}/${id}`);
  }

  getByMatricule(matricule: string): Observable<Employee> {
    return this.http.get<Employee>(`${this.apiUrl}/matricule/${matricule}`);
  }

  create(employee: Employee): Observable<Employee> {
    return this.http.post<Employee>(this.apiUrl, employee);
  }

  update(id: number, employee: Employee): Observable<Employee> {
    return this.http.put<Employee>(`${this.apiUrl}/${id}`, employee);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getByRole(role: string): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.apiUrl}/role/${role}`);
  }

  /** Returns employees whose N1 is the given manager */
  getSousN1(n1Id: number): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.apiUrl}/sous-n1/${n1Id}`);
  }

  assignHierarchy(id: number, n1Id?: number | null, n2Id?: number | null): Observable<void> {
    let url = `${this.apiUrl}/${id}/hierarchie?`;
    if (n1Id) url += `n1Id=${n1Id}&`;
    if (n2Id) url += `n2Id=${n2Id}&`;
    return this.http.patch<void>(url, {});
  }
}
