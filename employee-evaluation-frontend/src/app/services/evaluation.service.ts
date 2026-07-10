import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Evaluation {
  id?: number;
  nomEvaluation: string;
  dateDebut: string;
  dateFin: string;
  statut?: string;
  questions?: Question[];
}

export interface Question {
  id?: number;
  libelle: string;
  noteMax: number;
  ordre: number;
  evaluationId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class EvaluationService {
  private apiUrl = `${environment.apiUrl}/evaluations`;

  constructor(private http: HttpClient) {}

  // ============ CRUD EVALUATION ============
  getAll(): Observable<Evaluation[]> {
    return this.http.get<Evaluation[]>(this.apiUrl);
  }

  getById(id: number): Observable<Evaluation> {
    return this.http.get<Evaluation>(`${this.apiUrl}/${id}`);
  }

  create(evaluation: Evaluation): Observable<Evaluation> {
    return this.http.post<Evaluation>(this.apiUrl, evaluation);
  }

  update(id: number, evaluation: Evaluation): Observable<Evaluation> {
    return this.http.put<Evaluation>(`${this.apiUrl}/${id}`, evaluation);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // ============ STATUTS ============
  ouvrir(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/ouvrir`, {});
  }

  fermer(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/fermer`, {});
  }

  cloturer(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/cloturer`, {});
  }

  // ============ QUESTIONS ============
  getQuestions(evaluationId: number): Observable<Question[]> {
    return this.http.get<Question[]>(`${this.apiUrl}/${evaluationId}/questions`);
  }

  addQuestion(evaluationId: number, question: Question): Observable<Question> {
    return this.http.post<Question>(`${this.apiUrl}/${evaluationId}/questions`, question);
  }

  updateQuestion(questionId: number, question: Question): Observable<Question> {
    return this.http.put<Question>(`${this.apiUrl}/questions/${questionId}`, question);
  }

  deleteQuestion(questionId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/questions/${questionId}`);
  }
}
