import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/** Un point d'un graphique. Valeur et couleur viennent du serveur. */
export interface AssistantPoint {
  libelle: string;
  valeur: number;
  couleur: string;
}

/** Jeu de données prêt à être tracé, chiffré par le serveur. */
export interface AssistantChart {
  cle: string;
  type: 'DONUT' | 'BARRES' | 'LIGNE';
  titre: string;
  soustitre?: string | null;
  points: AssistantPoint[];
}

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
  /** Graphique à tracer, ou absent. */
  graphique?: AssistantChart | null;
  /** Questions de suivi proposées en un clic. */
  relances?: string[] | null;
}

/** Ce qui attend l'utilisateur à l'ouverture du panneau. */
export interface AssistantApercu {
  salutation: string;
  alertes: string[];
  suggestions: string[];
}

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly apiUrl = `${environment.apiUrl}/assistant`;

  constructor(private http: HttpClient) {}

  ask(question: string): Observable<AssistantReply> {
    return this.http.post<AssistantReply>(`${this.apiUrl}/ask`, { question });
  }

  /**
   * Alertes et amorces, calculées sans passer par le modèle.
   *
   * Sur erreur on rend un aperçu vide : le panneau doit s'ouvrir même si ce
   * complément d'information n'a pas pu être obtenu.
   */
  apercu(): Observable<AssistantApercu> {
    return this.http.get<AssistantApercu>(`${this.apiUrl}/apercu`).pipe(
      catchError(() => of({ salutation: '', alertes: [], suggestions: [] }))
    );
  }

  /** Efface aussi la conversation retenue côté serveur. */
  oublier(): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/conversation`).pipe(
      catchError(() => of(void 0))
    );
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
