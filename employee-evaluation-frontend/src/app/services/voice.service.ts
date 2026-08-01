import { Injectable, NgZone } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/**
 * Voix du navigateur : dictée et lecture à haute voix.
 *
 * Tout est natif — aucune clé, aucun service tiers, aucun envoi d'audio à un
 * serveur. La reconnaissance vocale reste préfixée `webkit` sur la plupart des
 * navigateurs et manque à certains : le composant doit donc toujours proposer
 * la saisie au clavier, la voix n'étant qu'un confort supplémentaire.
 */
@Injectable({ providedIn: 'root' })
export class VoiceService {

  /** Transcription finale d'une dictée. */
  private readonly resultat$ = new Subject<string>();
  /** Transcription partielle, affichée pendant que l'utilisateur parle. */
  private readonly partiel$ = new Subject<string>();
  private readonly fin$ = new Subject<void>();
  private readonly erreur$ = new Subject<string>();

  private reconnaissance: any = null;
  private enEcoute = false;

  constructor(private zone: NgZone) {}

  // ─── Capacités ───────────────────────────────────────────────────────────

  get dicteeSupportee(): boolean {
    return !!this.moteurReconnaissance();
  }

  get lectureSupportee(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window;
  }

  get ecouteEnCours(): boolean {
    return this.enEcoute;
  }

  private moteurReconnaissance(): any {
    if (typeof window === 'undefined') return null;
    return (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || null;
  }

  // ─── Flux ────────────────────────────────────────────────────────────────

  get transcription(): Observable<string> { return this.resultat$.asObservable(); }
  get transcriptionPartielle(): Observable<string> { return this.partiel$.asObservable(); }
  get finEcoute(): Observable<void> { return this.fin$.asObservable(); }
  get erreurs(): Observable<string> { return this.erreur$.asObservable(); }

  // ─── Dictée ──────────────────────────────────────────────────────────────

  /**
   * Démarre l'écoute. S'arrête d'elle-même à la fin de la phrase.
   *
   * Les rappels du moteur vocal arrivent hors de la zone Angular : sans
   * `zone.run`, l'affichage ne se rafraîchirait qu'au prochain événement.
   */
  ecouter(): void {
    const Moteur = this.moteurReconnaissance();
    if (!Moteur || this.enEcoute) return;

    this.reconnaissance = new Moteur();
    this.reconnaissance.lang = 'fr-FR';
    this.reconnaissance.continuous = false;
    this.reconnaissance.interimResults = true;
    this.reconnaissance.maxAlternatives = 1;

    this.reconnaissance.onresult = (event: any) => {
      let final = '';
      let partiel = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const texte = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          final += texte;
        } else {
          partiel += texte;
        }
      }
      this.zone.run(() => {
        if (partiel) this.partiel$.next(partiel);
        if (final.trim()) this.resultat$.next(final.trim());
      });
    };

    this.reconnaissance.onerror = (event: any) => {
      this.zone.run(() => {
        this.enEcoute = false;
        this.erreur$.next(this.messageErreur(event?.error));
      });
    };

    this.reconnaissance.onend = () => {
      this.zone.run(() => {
        this.enEcoute = false;
        this.fin$.next();
      });
    };

    try {
      this.reconnaissance.start();
      this.enEcoute = true;
    } catch {
      // `start()` sur une session déjà ouverte lève : sans effet ici.
      this.enEcoute = false;
    }
  }

  arreterEcoute(): void {
    if (this.reconnaissance && this.enEcoute) {
      try { this.reconnaissance.stop(); } catch { /* déjà arrêtée */ }
      this.enEcoute = false;
    }
  }

  private messageErreur(code: string): string {
    switch (code) {
      case 'not-allowed':
      case 'service-not-allowed':
        return 'Accès au micro refusé. Autorisez-le dans les réglages du navigateur.';
      case 'no-speech':
        return 'Aucune parole détectée. Réessayez.';
      case 'audio-capture':
        return 'Aucun micro détecté sur cet appareil.';
      case 'network':
        return 'La reconnaissance vocale exige une connexion réseau.';
      default:
        return 'La dictée a échoué. Utilisez le clavier.';
    }
  }

  // ─── Lecture ─────────────────────────────────────────────────────────────

  /** Lit un texte à voix haute, en coupant la lecture précédente. */
  lire(texte: string): void {
    if (!this.lectureSupportee || !texte?.trim()) return;

    window.speechSynthesis.cancel();

    const enonce = new SpeechSynthesisUtterance(texte);
    enonce.lang = 'fr-FR';
    enonce.rate = 1.02;
    enonce.pitch = 1;

    // Une voix française donne une prononciation nettement plus juste que la
    // voix par défaut du système, souvent anglophone.
    const voix = window.speechSynthesis.getVoices().find(v => v.lang?.startsWith('fr'));
    if (voix) enonce.voice = voix;

    window.speechSynthesis.speak(enonce);
  }

  arreterLecture(): void {
    if (this.lectureSupportee) {
      window.speechSynthesis.cancel();
    }
  }
}
