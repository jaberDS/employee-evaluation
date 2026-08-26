import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DashboardStats {
  totalEmployees: number;
  activeEmployees: number;
  totalCampagnes: number;
  openCampagnes: number;
  completedEvaluations: number;
  pendingEvaluations: number;
  averageNote: number;
  prochaineCampagneNom?: string;
  prochaineCampagneDateDebut?: string;
  prochaineCampagneParticipants?: number;
  prochaineCampagneDureeJours?: number;
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private apiUrl = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/stats`);
  }
}
