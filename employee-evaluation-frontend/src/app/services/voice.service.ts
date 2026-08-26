import { Injectable, NgZone } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/**
 * Silence toléré avant de considérer la phrase terminée.
 *
 * Dix secondes, c'est long pour une machine et court pour une personne qui
 * cherche ses mots : on préfère attendre pour rien qu'amputer une phrase.
 */
const SILENCE_MS = 10_000;
/** Durée maximale d'une dictée : garde-fou si le micro capte un bruit continu. */
const ECOUTE_MAX_MS = 90_000;

/** Longueur de découpe des énoncés lus à voix haute. */
const TAILLE_ENONCE = 170;

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

  /** Transcription complète, émise une seule fois en fin de dictée. */
  private readonly resultat$ = new Subject<string>();
  /** Texte en construction, affiché pendant que l'utilisateur parle. */
  private readonly partiel$ = new Subject<string>();
  private readonly fin$ = new Subject<void>();
  private readonly erreur$ = new Subject<string>();
  private readonly finLecture$ = new Subject<void>();

  private reconnaissance: any = null;
  private enEcoute = false;

  /** Segments définitifs accumulés depuis le début de la dictée. */
  private tampon = '';
  private minuteurSilence: any = null;
  private minuteurMax: any = null;
  /** Arrêt volontaire : distingue « j'ai fini » d'une coupure du moteur. */
  private arretDemande = false;

  /**
   * Énoncés en cours de lecture.
   *
   * Chrome libère les `SpeechSynthesisUtterance` non référencés avant la fin de
   * la lecture, ce qui la coupe au bout d'une seconde ou deux. Les garder ici
   * suffit à l'en empêcher.
   */
  private enonces: SpeechSynthesisUtterance[] = [];
  private fileLecture: string[] = [];
  private lectureEnCours = false;
  private jetonLecture = 0;

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

  get lectureActive(): boolean {
    return this.lectureEnCours;
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
  /** Émis quand la dernière phrase a fini d'être prononcée. */
  get finLecture(): Observable<void> { return this.finLecture$.asObservable(); }

  // ─── Dictée ──────────────────────────────────────────────────────────────

  /**
   * Démarre l'écoute et la maintient ouverte jusqu'à un silence franc.
   *
   * `continuous = false` rendait la main dès la première respiration, coupant
   * les phrases en deux : le moteur émettait un résultat « final » après une
   * pause d'un demi-mot. On écoute donc en continu, on accumule les segments,
   * et c'est un silence de {@link SILENCE_MS} qui décide de la fin — ce que
   * l'oreille humaine ferait aussi.
   *
   * Les rappels du moteur vocal arrivent hors de la zone Angular : sans
   * `zone.run`, l'affichage ne se rafraîchirait qu'au prochain événement.
   */
  ecouter(): void {
    const Moteur = this.moteurReconnaissance();
    if (!Moteur || this.enEcoute) return;

    this.tampon = '';
    this.arretDemande = false;

    this.reconnaissance = new Moteur();
    this.reconnaissance.lang = 'fr-FR';
    this.reconnaissance.continuous = true;
    this.reconnaissance.interimResults = true;
    this.reconnaissance.maxAlternatives = 1;

    this.reconnaissance.onresult = (event: any) => {
      let nouveauFinal = '';
      let partiel = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const texte = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          nouveauFinal += texte;
        } else {
          partiel += texte;
        }
      }

      if (nouveauFinal.trim()) {
        this.tampon = (this.tampon + ' ' + nouveauFinal.trim()).trim();
      }

      this.zone.run(() => {
        // Le tampon est joint au partiel : la personne voit sa phrase entière
        // se construire, pas seulement le dernier fragment.
        const apercu = (this.tampon + ' ' + partiel).trim();
        if (apercu) this.partiel$.next(apercu);
      });

      this.reporterSilence();
    };

    this.reconnaissance.onspeechstart = () => this.reporterSilence();

    this.reconnaissance.onerror = (event: any) => {
      const code = event?.error;

      // `no-speech` n'est pas une erreur ici : Chrome le lève dès quelques
      // secondes de silence, alors qu'on en accorde dix. `onend` suit et
      // relance le moteur — laisser passer un message d'erreur à ce moment-là
      // reviendrait à réprimander quelqu'un qui réfléchit.
      if (code === 'no-speech') return;
      // `aborted` provient de notre propre arrêt : rien à signaler.
      if (code === 'aborted') return;

      this.arretDemande = true;
      this.zone.run(() => {
        this.nettoyerMinuteurs();
        this.enEcoute = false;
        this.erreur$.next(this.messageErreur(code));
      });
    };

    this.reconnaissance.onend = () => {
      // Chrome referme la session de lui-même après quelques secondes de
      // silence, bien avant nos dix. Tant que le compte à rebours n'a pas
      // expiré, on relance donc le moteur : c'est notre minuteur qui décide de
      // la fin de la dictée, pas celui du navigateur.
      if (!this.arretDemande && this.enEcoute) {
        try {
          this.reconnaissance.start();
          return;
        } catch {
          // Relance impossible (session encore en cours de fermeture) : on
          // termine proprement plutôt que de laisser le micro dans les limbes.
        }
      }

      this.nettoyerMinuteurs();
      const texte = this.tampon.trim();
      this.tampon = '';

      this.zone.run(() => {
        this.enEcoute = false;
        if (texte) this.resultat$.next(texte);
        this.fin$.next();
      });
    };

    try {
      this.reconnaissance.start();
      this.enEcoute = true;
      this.reporterSilence();
      this.minuteurMax = setTimeout(() => this.arreterEcoute(), ECOUTE_MAX_MS);
    } catch {
      // `start()` sur une session déjà ouverte lève : sans effet ici.
      this.enEcoute = false;
    }
  }

  /** Repousse l'échéance de silence à chaque signe de parole. */
  private reporterSilence(): void {
    if (this.minuteurSilence) clearTimeout(this.minuteurSilence);
    this.minuteurSilence = setTimeout(() => {
      this.arretDemande = true;
      this.arreterEcoute();
    }, SILENCE_MS);
  }

  private nettoyerMinuteurs(): void {
    if (this.minuteurSilence) { clearTimeout(this.minuteurSilence); this.minuteurSilence = null; }
    if (this.minuteurMax) { clearTimeout(this.minuteurMax); this.minuteurMax = null; }
  }

  /**
   * Clôt la dictée en restituant ce qui a été entendu.
   *
   * `arretDemande` doit être posé avant `stop()`, sinon `onend` prendrait cette
   * fermeture pour une coupure de Chrome et relancerait le micro.
   */
  arreterEcoute(): void {
    this.arretDemande = true;
    this.nettoyerMinuteurs();
    if (this.reconnaissance && this.enEcoute) {
      // `stop()` laisse le moteur restituer ce qu'il a déjà entendu, là où
      // `abort()` le jetterait : on ne perd pas la fin de la phrase.
      try { this.reconnaissance.stop(); } catch { /* déjà arrêtée */ }
    }
  }

  /** Abandonne la dictée sans en transmettre le contenu. */
  annulerEcoute(): void {
    this.arretDemande = true;
    this.nettoyerMinuteurs();
    this.tampon = '';
    if (this.reconnaissance && this.enEcoute) {
      try { this.reconnaissance.abort(); } catch { /* déjà arrêtée */ }
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

  /**
   * Lit un texte à voix haute, du début à la fin.
   *
   * La lecture est découpée en courtes phrases enchaînées. Un énoncé unique et
   * long se fait tronquer par Chrome au bout de quelques secondes ; une file de
   * fragments courts, relancés l'un après l'autre, passe entière.
   *
   * `finLecture` est émis à la toute fin — c'est ce signal, et non un délai
   * arbitraire, qui doit déclencher ce qui vient après (une navigation, par
   * exemple), pour ne pas couper la phrase en cours.
   */
  lire(texte: string): void {
    if (!this.lectureSupportee || !texte?.trim()) {
      this.zone.run(() => this.finLecture$.next());
      return;
    }

    this.arreterLecture();

    const jeton = ++this.jetonLecture;
    this.fileLecture = this.decouper(texte);
    this.lectureEnCours = true;

    // Au premier appel de la session, la liste des voix est souvent encore
    // vide : le navigateur la charge de façon asynchrone.
    const voix = window.speechSynthesis.getVoices();
    if (!voix.length) {
      const attendre = () => {
        window.speechSynthesis.removeEventListener('voiceschanged', attendre);
        if (jeton === this.jetonLecture) this.enchainer(jeton);
      };
      window.speechSynthesis.addEventListener('voiceschanged', attendre);
      // Certains navigateurs n'émettent jamais l'événement : on n'attend pas
      // indéfiniment une voix française pour commencer à parler.
      setTimeout(() => {
        window.speechSynthesis.removeEventListener('voiceschanged', attendre);
        if (jeton === this.jetonLecture && !this.enonces.length) this.enchainer(jeton);
      }, 250);
      return;
    }

    this.enchainer(jeton);
  }

  /** Prononce le fragment suivant, puis s'appelle de nouveau. */
  private enchainer(jeton: number): void {
    if (jeton !== this.jetonLecture) return;

    const fragment = this.fileLecture.shift();
    if (!fragment) {
      this.enonces = [];
      this.lectureEnCours = false;
      this.zone.run(() => this.finLecture$.next());
      return;
    }

    const enonce = new SpeechSynthesisUtterance(fragment);
    enonce.lang = 'fr-FR';
    enonce.rate = 1;
    enonce.pitch = 1;
    enonce.volume = 1;

    // Une voix française donne une prononciation nettement plus juste que la
    // voix par défaut du système, souvent anglophone.
    const voix = window.speechSynthesis.getVoices()
      .find(v => v.lang === 'fr-FR') ?? window.speechSynthesis.getVoices()
      .find(v => v.lang?.startsWith('fr'));
    if (voix) enonce.voice = voix;

    enonce.onend = () => this.enchainer(jeton);
    // Une erreur sur un fragment ne doit pas figer la file : on poursuit, et le
    // signal de fin finit par arriver quoi qu'il advienne.
    enonce.onerror = () => this.enchainer(jeton);

    this.enonces.push(enonce);
    window.speechSynthesis.speak(enonce);
  }

  /**
   * Découpe un texte en fragments prononçables, sur les fins de phrase quand
   * c'est possible, sur les espaces sinon.
   */
  private decouper(texte: string): string[] {
    const phrases = texte.trim().split(/(?<=[.!?…:;])\s+/);
    const morceaux: string[] = [];

    for (const phrase of phrases) {
      if (phrase.length <= TAILLE_ENONCE) {
        if (phrase.trim()) morceaux.push(phrase.trim());
        continue;
      }
      // Phrase trop longue (énumération, absence de ponctuation) : on la coupe
      // sur les espaces pour ne jamais tronquer un mot.
      let reste = phrase.trim();
      while (reste.length > TAILLE_ENONCE) {
        let coupe = reste.lastIndexOf(' ', TAILLE_ENONCE);
        if (coupe <= 0) coupe = TAILLE_ENONCE;
        morceaux.push(reste.slice(0, coupe).trim());
        reste = reste.slice(coupe).trim();
      }
      if (reste) morceaux.push(reste);
    }

    return morceaux;
  }

  arreterLecture(): void {
    this.jetonLecture++;
    this.fileLecture = [];
    this.enonces = [];
    this.lectureEnCours = false;
    if (this.lectureSupportee) {
      window.speechSynthesis.cancel();
    }
  }
}
