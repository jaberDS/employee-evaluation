import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface FicheEvaluation {
  id: number;
  employeId: number;
  employeNom: string;
  employePrenom: string;
  evaluationId: number;
  evaluationNom: string;
  dateCreation: string;
  reponsesN1: { [key: number]: number };
  noteN1: number;
  commentaireN1: string;
  decisionN2: string | null;
  commentaireN2: string | null;
  decisionEmploye: string | null;
  noteFinale: number | null;
  statut: string;
}

export interface EvaluationN1Request {
  employeId: number;
  evaluationId: number;
  reponses: { [key: number]: number };
  commentaire: string;
}

export interface ValidationN2Request {
  accepte: boolean;
  commentaire: string;
}

@Injectable({
  providedIn: 'root'
})
export class FicheService {
  private apiUrl = `${environment.apiUrl}/fiches`;

  constructor(private http: HttpClient) {}

  // ============ EVALUATION N+1 ============
  evaluerParN1(request: EvaluationN1Request): Observable<FicheEvaluation> {
    return this.http.post<FicheEvaluation>(`${this.apiUrl}/evaluer`, request);
  }

  // ============ VALIDATION N+2 ============
  validerParN2(ficheId: number, request: ValidationN2Request): Observable<FicheEvaluation> {
    return this.http.patch<FicheEvaluation>(`${this.apiUrl}/${ficheId}/n2`, request);
  }

  // ============ VALIDATION EMPLOYÉ ============
  validerParEmploye(ficheId: number, accepte: boolean): Observable<FicheEvaluation> {
    return this.http.patch<FicheEvaluation>(`${this.apiUrl}/${ficheId}/employe?accepte=${accepte}`, {});
  }

  // ============ CONSULTATION ============
  getById(id: number): Observable<FicheEvaluation> {
    return this.http.get<FicheEvaluation>(`${this.apiUrl}/${id}`);
  }

  getByEmploye(employeId: number): Observable<FicheEvaluation[]> {
    return this.http.get<FicheEvaluation[]>(`${this.apiUrl}/employe/${employeId}`);
  }

  getByEvaluation(evaluationId: number): Observable<FicheEvaluation[]> {
    return this.http.get<FicheEvaluation[]>(`${this.apiUrl}/evaluation/${evaluationId}`);
  }

  getByStatut(statut: string): Observable<FicheEvaluation[]> {
    return this.http.get<FicheEvaluation[]>(`${this.apiUrl}/statut/${statut}`);
  }
}
