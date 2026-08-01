import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/** Réponse de l'assistant. Le chemin est déjà validé par le serveur. */
export interface AssistantReply {
  /** Texte à afficher et à lire à voix haute. */
  reponse: string;
  /** Clé de destination retenue, ou absente. */
  cible?: string | null;
  /** Chemin Angular prêt pour le routeur, ou absent si aucune navigation. */
  chemin?: string | null;
  /** Libellé de la destination, pour annoncer la redirection. */
  libelle?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly apiUrl = `${environment.apiUrl}/assistant`;

  constructor(private http: HttpClient) {}

  ask(question: string): Observable<AssistantReply> {
    return this.http.post<AssistantReply>(`${this.apiUrl}/ask`, { question });
  }

  /**
   * L'assistant n'est proposé que si le serveur détient une clé.
   *
   * Sur erreur on répond « indisponible » plutôt que de propager : un assistant
   * absent ne doit jamais empêcher le reste de l'application de fonctionner.
   */
  disponible(): Observable<boolean> {
    return this.http.get<{ disponible: boolean }>(`${this.apiUrl}/status`).pipe(
      map(r => !!r.disponible),
      catchError(() => of(false))
    );
  }

  describeError(err: any): string {
    if (err?.status === 429) {
      return 'Trop de questions en peu de temps. Patientez un instant.';
    }
    if (err?.status === 503) {
      return err?.error?.message || 'L\'assistant est momentanément indisponible.';
    }
    if (err?.status === 0) {
      return 'Impossible de contacter le serveur.';
    }
    return err?.error?.message || 'La demande n\'a pas abouti.';
  }
}
