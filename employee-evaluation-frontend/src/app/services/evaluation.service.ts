import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Evaluation, Question, TypeAffectation } from '../models/evaluation.model';

export { Evaluation, Question, TypeAffectation } from '../models/evaluation.model';

@Injectable({
  providedIn: 'root'
})
export class EvaluationService {
  private apiUrl = `${environment.apiUrl}/evaluations`;

  constructor(private http: HttpClient) {}

  // ============ CRUD EVALUATION ============
  getAll(type?: TypeAffectation): Observable<Evaluation[]> {
    const options = type ? { params: new HttpParams().set('type', type) } : {};
    return this.http.get<Evaluation[]>(this.apiUrl, options);
  }

  getById(id: number): Observable<Evaluation> {
    return this.http.get<Evaluation>(`${this.apiUrl}/${id}`);
  }

  create(evaluation: Evaluation): Observable<Evaluation> {
    return this.http.post<Evaluation>(this.apiUrl, evaluation);
  }

  /**
   * Crée le couple de campagnes Agence + Siège à partir d'une seule saisie.
   * Retourne les deux campagnes créées (Agence en premier).
   */
  createPaire(evaluation: Partial<Evaluation>): Observable<Evaluation[]> {
    return this.http.post<Evaluation[]>(`${this.apiUrl}/paire`, evaluation);
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

  getQuestionById(questionId: number): Observable<Question> {
    return this.http.get<Question>(`${this.apiUrl}/questions/${questionId}`);
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

  toggleActif(questionId: number): Observable<Question> {
    return this.http.patch<Question>(`${this.apiUrl}/questions/${questionId}/toggle-actif`, {});
  }
}
