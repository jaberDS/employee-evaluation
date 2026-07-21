import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ActiviteLog {
  id: number;
  type: string;
  description: string;
  acteurId?: number;
  acteurNom?: string;
  acteurRole?: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class ActiviteService {
  private apiUrl = `${environment.apiUrl}/activites`;

  private activitiesSubject = new BehaviorSubject<ActiviteLog[]>([]);
  /** Flux partagé des dernières activités — toute vue qui le consomme reste synchronisée. */
  activities$ = this.activitiesSubject.asObservable();

  constructor(private http: HttpClient) {}

  /** Recharge les activités depuis le serveur et met à jour le flux partagé. */
  refresh(limit = 10): Observable<ActiviteLog[]> {
    return this.http.get<ActiviteLog[]>(`${this.apiUrl}?limit=${limit}`).pipe(
      tap(activities => this.activitiesSubject.next(activities))
    );
  }

  getRecent(limit = 10): Observable<ActiviteLog[]> {
    return this.refresh(limit);
  }

  deleteAll(): Observable<void> {
    return this.http.delete<void>(this.apiUrl).pipe(
      tap(() => this.activitiesSubject.next([]))
    );
  }
}
