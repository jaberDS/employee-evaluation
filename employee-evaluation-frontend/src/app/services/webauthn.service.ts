import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  browserSupportsWebAuthn,
  startAuthentication,
  startRegistration
} from '@simplewebauthn/browser';
import { environment } from '../../environments/environment';
import { AuthResponse, LoginResponse } from './auth.service';

export interface CeremonyOptions {
  ceremonyId: string;
  optionsJSON: string;
}

export interface PasskeySummary {
  id: number;
  label: string;
  transports: string | null;
  creeLe: string;
  derniereUtilisation: string | null;
  backedUp: boolean | null;
}

/**
 * Type d'appareil demandé.
 *
 * Sans indication, Windows affiche Windows Hello et enterre « téléphone » sous
 * un sous-menu. PHONE force le navigateur à présenter directement le QR code.
 */
export type AuthenticatorPreference = 'THIS_DEVICE' | 'PHONE' | 'SECURITY_KEY' | 'ANY';

/**
 * Clés d'accès (passkeys).
 *
 * Le parcours « QR sur le PC, empreinte sur le téléphone » est le transport
 * hybride natif de WebAuthn : le navigateur affiche le QR lui-même dès lors
 * qu'on ne restreint pas les authentificateurs. Rien à coder de ce côté.
 */
@Injectable({ providedIn: 'root' })
export class WebauthnService {

  private authUrl = `${environment.apiUrl}/auth`;
  private mfaUrl = `${environment.apiUrl}/mfa/webauthn`;

  constructor(private http: HttpClient) {}

  isSupported(): boolean {
    return browserSupportsWebAuthn();
  }

  // ─── Enrôlement (profil, session requise) ─────────────────────────────────

  registerOptions(appareil: AuthenticatorPreference = 'ANY'): Observable<CeremonyOptions> {
    return this.http.post<CeremonyOptions>(
      `${this.mfaUrl}/register/options`, {}, { params: { appareil } });
  }

  registerVerify(ceremonyId: string, label: string, credential: string): Observable<any> {
    return this.http.post(`${this.mfaUrl}/register/verify`, { ceremonyId, label, credential });
  }

  listPasskeys(): Observable<PasskeySummary[]> {
    return this.http.get<PasskeySummary[]>(`${this.mfaUrl}/credentials`);
  }

  renamePasskey(id: number, label: string): Observable<any> {
    return this.http.patch(`${this.mfaUrl}/credentials/${id}`, { label });
  }

  deletePasskey(id: number, currentPassword: string): Observable<any> {
    return this.http.request('delete', `${this.mfaUrl}/credentials/${id}`, {
      body: { currentPassword }
    });
  }

  // ─── Second facteur à la connexion ────────────────────────────────────────

  loginOptions(mfaToken: string, appareil: AuthenticatorPreference = 'ANY'): Observable<CeremonyOptions> {
    return this.http.post<CeremonyOptions>(`${this.authUrl}/mfa/webauthn/options`,
      { mfaToken }, { params: { appareil } });
  }

  loginVerify(mfaToken: string, ceremonyId: string, credential: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/mfa/webauthn/verify`,
      { mfaToken, ceremonyId, credential });
  }

  // ─── Récupération de compte ───────────────────────────────────────────────

  recoveryStart(matricule: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/recovery/start`, { matricule });
  }

  recoveryOptions(mfaToken: string, appareil: AuthenticatorPreference = 'ANY'): Observable<CeremonyOptions> {
    return this.http.post<CeremonyOptions>(`${this.authUrl}/recovery/webauthn/options`,
      { mfaToken }, { params: { appareil } });
  }

  recoveryVerify(mfaToken: string, ceremonyId: string, credential: string): Observable<{ resetToken: string }> {
    return this.http.post<{ resetToken: string }>(`${this.authUrl}/recovery/webauthn/verify`,
      { mfaToken, ceremonyId, credential });
  }

  resetPassword(resetToken: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.authUrl}/recovery/reset-password`, { resetToken, newPassword });
  }

  // ─── Appels navigateur ────────────────────────────────────────────────────

  /** Déclenche l'invite système (empreinte, visage, ou téléphone via QR). */
  async promptRegistration(optionsJSON: string): Promise<string> {
    const response = await startRegistration({ optionsJSON: JSON.parse(optionsJSON) });
    return JSON.stringify(response);
  }

  async promptAuthentication(optionsJSON: string): Promise<string> {
    const response = await startAuthentication({ optionsJSON: JSON.parse(optionsJSON) });
    return JSON.stringify(response);
  }

  /** Traduit les erreurs de l'API navigateur en message affichable. */
  describeError(error: any): string {
    const name = error?.name ?? '';
    if (name === 'NotAllowedError') {
      return 'Vérification annulée ou expirée. Réessayez.';
    }
    if (name === 'InvalidStateError') {
      return 'Cet appareil est déjà enregistré sur votre compte.';
    }
    if (name === 'NotSupportedError') {
      return 'Cet appareil ne prend pas en charge les clés d\'accès.';
    }
    if (name === 'SecurityError') {
      return 'Connexion non sécurisée. Les clés d\'accès exigent HTTPS.';
    }
    return error?.message || 'La vérification a échoué. Réessayez.';
  }
}

export type { AuthResponse };
