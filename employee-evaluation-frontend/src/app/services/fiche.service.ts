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
  reponsesN1: { [key: number]: number } | null;
  noteN1: number | null;
  commentaireN1: string | null;
  decisionN2: string | null;
  commentaireN2: string | null;
  decisionEmploye: string | null;
  noteFinale: number | null;
  statut: string; // EN_ATTENTE | EN_COURS_N1 | EN_ATTENTE_N2 | A_REVISER | EN_ATTENTE_EMPLOYE | CLOTUREE
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

@Injectable({ providedIn: 'root' })
export class FicheService {
  private apiUrl = `${environment.apiUrl}/fiches`;

  constructor(private http: HttpClient) {}

  evaluerParN1(request: EvaluationN1Request): Observable<FicheEvaluation> {
    return this.http.post<FicheEvaluation>(`${this.apiUrl}/evaluer`, request);
  }

  validerParN2(ficheId: number, request: ValidationN2Request): Observable<FicheEvaluation> {
    return this.http.patch<FicheEvaluation>(`${this.apiUrl}/${ficheId}/n2`, request);
  }

  validerParEmploye(ficheId: number, accepte: boolean): Observable<FicheEvaluation> {
    return this.http.patch<FicheEvaluation>(`${this.apiUrl}/${ficheId}/employe?accepte=${accepte}`, {});
  }

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

  /** Get all fiches for the employees managed by this N+1 */
  getByN1(n1Id: number): Observable<FicheEvaluation[]> {
    return this.http.get<FicheEvaluation[]>(`${this.apiUrl}/n1/${n1Id}`);
  }
}
