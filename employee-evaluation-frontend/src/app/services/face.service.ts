import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse } from './auth.service';

/** Une consigne de vivacité tirée au sort par le serveur. */
export interface LivenessStep {
  /** BLINK | TURN_LEFT | TURN_RIGHT | SMILE */
  action: string;
  /** Texte déjà traduit, prêt à afficher. */
  instruction: string;
  /** Durée conseillée de capture, en millisecondes. */
  dureeMs: number;
}

export interface FaceChallenge {
  ceremonyId: string;
  steps: LivenessStep[];
}

export interface FaceStatus {
  enrolled: boolean;
  qualite?: number;
  creeLe?: string;
}

/** Une trame envoyée au serveur : image JPEG en base64 + l'étape visée. */
export interface FaceFrame {
  action: string;
  image: string;
}

@Injectable({ providedIn: 'root' })
export class FaceService {
  private readonly mfaUrl = `${environment.apiUrl}/mfa/face`;
  private readonly authUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  // ─── Inscription (utilisateur déjà connecté) ────────────────────────────

  status(): Observable<FaceStatus> {
    return this.http.get<FaceStatus>(this.mfaUrl);
  }

  enrollChallenge(): Observable<FaceChallenge> {
    return this.http.post<FaceChallenge>(`${this.mfaUrl}/challenge`, {});
  }

  enroll(ceremonyId: string, frames: FaceFrame[]): Observable<FaceStatus> {
    return this.http.post<FaceStatus>(`${this.mfaUrl}/enroll`, { ceremonyId, frames });
  }

  remove(currentPassword: string): Observable<void> {
    return this.http.request<void>('delete', this.mfaUrl, { body: { currentPassword } });
  }

  // ─── Connexion / récupération (jeton en attente dans le corps) ──────────

  loginChallenge(mfaToken: string): Observable<FaceChallenge> {
    return this.http.post<FaceChallenge>(`${this.authUrl}/mfa/face/challenge`, { mfaToken });
  }

  loginVerify(mfaToken: string, ceremonyId: string, frames: FaceFrame[]): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/mfa/face/verify`,
      { mfaToken, ceremonyId, frames });
  }

  recoveryChallenge(mfaToken: string): Observable<FaceChallenge> {
    return this.http.post<FaceChallenge>(`${this.authUrl}/recovery/face/challenge`, { mfaToken });
  }

  recoveryVerify(mfaToken: string, ceremonyId: string, frames: FaceFrame[]): Observable<{ resetToken: string }> {
    return this.http.post<{ resetToken: string }>(`${this.authUrl}/recovery/face/verify`,
      { mfaToken, ceremonyId, frames });
  }

  // ─── Capacités du navigateur ────────────────────────────────────────────

  /**
   * La caméra n'est exposée qu'en contexte sécurisé (HTTPS ou localhost).
   * Sur une IP de réseau local en HTTP, `mediaDevices` est simplement absent.
   */
  isSupported(): boolean {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia) && window.isSecureContext;
  }

  describeError(err: any): string {
    const nom = err?.name || '';
    if (nom === 'NotAllowedError') {
      return 'Accès à la caméra refusé. Autorisez-le dans les réglages du navigateur.';
    }
    if (nom === 'NotFoundError' || nom === 'DevicesNotFoundError') {
      return 'Aucune caméra détectée sur cet appareil.';
    }
    if (nom === 'NotReadableError') {
      return 'La caméra est déjà utilisée par une autre application.';
    }
    if (nom === 'OverconstrainedError') {
      return 'La caméra ne supporte pas la résolution demandée.';
    }
    return err?.error?.message || err?.message || 'La vérification faciale a échoué.';
  }
}
