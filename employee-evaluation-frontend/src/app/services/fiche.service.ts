import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TypeAffectation } from '../models/evaluation.model';

export { TypeAffectation };

export interface FicheEvaluation {
  id: number;
  employeId: number;
  employeNom: string;
  employePrenom: string;
  typeAffectation: TypeAffectation;
  evaluationId: number;
  evaluationNom: string;
  evaluationStatut: string | null; // BROUILLON | OUVERTE | FERMEE | CLOTUREE
  dateCreation: string;
  reponsesN1: { [key: number]: number } | null;
  noteN1: number | null;
  commentaireN1: string | null;
  decisionN2: string | null;
  commentaireN2: string | null;
  decisionEmploye: string | null;
  commentaireEmploye: string | null;
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

  validerParEmploye(ficheId: number, accepte: boolean, commentaire?: string): Observable<FicheEvaluation> {
    let url = `${this.apiUrl}/${ficheId}/employe?accepte=${accepte}`;
    if (commentaire) url += `&commentaire=${encodeURIComponent(commentaire)}`;
    return this.http.patch<FicheEvaluation>(url, {});
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

  /** Get fiches for the employees managed by this N+2, optionally filtered by statut */
  getByN2(n2Id: number, statut?: string): Observable<FicheEvaluation[]> {
    const url = `${this.apiUrl}/n2/${n2Id}`;
    return this.http.get<FicheEvaluation[]>(statut ? `${url}?statut=${statut}` : url);
  }

  /** Supprime une fiche clôturée, campagne clôturée, validée par le N+2 et l'employé. */
  delete(ficheId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${ficheId}`);
  }

  /** Supprime en masse toutes les fiches éligibles des subordonnés de ce N+1. Retourne le nombre supprimé. */
  deleteAllEligibleByN1(n1Id: number): Observable<number> {
    return this.http.delete<number>(`${this.apiUrl}/n1/${n1Id}`);
  }
}
