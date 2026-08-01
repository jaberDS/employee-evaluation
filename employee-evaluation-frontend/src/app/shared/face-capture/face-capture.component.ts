import {
  AfterViewInit, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input,
  NgZone, OnDestroy, Output, ViewChild
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import gsap from 'gsap';
import {
  FaceChallenge, FaceFrame, FaceService, LivenessStep
} from '../../services/face.service';
import { AuthResponse } from '../../services/auth.service';

/** Contexte d'utilisation : détermine quels endpoints appeler. */
export type FaceMode = 'ENROLL' | 'LOGIN' | 'RECOVERY';

type Phase = 'INTRO' | 'STARTING' | 'COUNTDOWN' | 'CAPTURE' | 'ANALYZING' | 'SUCCESS' | 'ERROR';

/**
 * Trames prises par consigne.
 *
 * Chaque trame coûte une détection complète côté Python (~0,5 s sur CPU), et la
 * cérémonie entière doit tenir dans le délai d'attente de Spring. Quatre trames
 * réparties sur la fenêtre suffisent à attraper un clignement de 100 à 400 ms,
 * là où une seule le ratait presque toujours.
 */
const RAFALE = 4;

/**
 * Temps laissé pour lire la consigne et amorcer le geste, avant la première
 * prise de vue.
 *
 * Valeur fixe et non proportionnelle : lire « Souriez » prend le même temps
 * quelle que soit la durée allouée à la consigne. Exprimé en fraction, les
 * consignes courtes ne laissaient presque rien — la première trame partait
 * avant même que l'utilisateur ait fini de lire.
 */
const DELAI_LECTURE_MS = 1100;

/** Marge finale : la dernière prise ne doit jamais mordre sur la consigne suivante. */
const MARGE_FIN_MS = 450;

@Component({
  selector: 'app-face-capture',
  standalone: false,
  templateUrl: './face-capture.component.html',
  styleUrls: ['./face-capture.component.css'],
  // La classe portée par l'hôte laisse la feuille de style décliner deux
  // identités distinctes sans dupliquer le composant.
  host: {
    '[class.fc-mode-enroll]': "mode === 'ENROLL'",
    '[class.fc-mode-verify]': "mode !== 'ENROLL'"
  }
})
export class FaceCaptureComponent implements AfterViewInit, OnDestroy {

  /** ENROLL depuis le profil, LOGIN/RECOVERY depuis les pages publiques. */
  @Input() mode: FaceMode = 'ENROLL';
  /** Jeton d'étape MFA, requis hors ENROLL. */
  @Input() mfaToken = '';

  /** Inscription réussie (mode ENROLL). */
  @Output() enrolled = new EventEmitter<void>();
  /** Session délivrée (mode LOGIN). */
  @Output() authenticated = new EventEmitter<AuthResponse>();
  /** Jeton de réinitialisation obtenu (mode RECOVERY). */
  @Output() recovered = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();
  /** Le jeton d'étape a expiré : l'appelant doit revenir au début. */
  @Output() expired = new EventEmitter<void>();

  @ViewChild('video') videoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('canvas') canvasRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('stage') stageRef?: ElementRef<HTMLElement>;

  phase: Phase = 'INTRO';
  errorMessage = '';
  /** Conseil affiché sous le message d'erreur, adapté à la cause. */
  errorHint = '';
  cameraReady = false;

  steps: LivenessStep[] = [];
  currentIndex = -1;
  /** Progression de la consigne en cours, dans [0, 1]. */
  progress = 0;
  /** Trames déjà prises pour la consigne en cours : nourrit les jalons. */
  burstCount = 0;
  /** Décompte affiché avant la première consigne. */
  countdown = 3;
  /** Vrai pendant la fenêtre de rafale : l'anneau passe en « enregistrement ». */
  recording = false;

  private stream: MediaStream | null = null;
  private ceremonyId = '';
  private frames: FaceFrame[] = [];
  private timers: ReturnType<typeof setTimeout>[] = [];
  private progressTween?: gsap.core.Tween;
  private ambientTimeline?: gsap.core.Timeline;
  private analyzeTimeline?: gsap.core.Timeline;

  constructor(
    private face: FaceService,
    private zone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngAfterViewInit(): void {
    this.animateIntro();
  }

  ngOnDestroy(): void {
    this.teardown();
  }

  get supported(): boolean {
    return this.face.isSupported();
  }

  get currentStep(): LivenessStep | null {
    return this.currentIndex >= 0 && this.currentIndex < this.steps.length
      ? this.steps[this.currentIndex]
      : null;
  }

  /** Périmètre du cercle de progression (r = 92 dans le SVG). */
  readonly ringCircumference = 2 * Math.PI * 92;

  get ringOffset(): number {
    return this.ringCircumference * (1 - this.progress);
  }

  /** Jalons de rafale affichés sous l'anneau. */
  get burstSlots(): number[] {
    return Array.from({ length: RAFALE }, (_, i) => i);
  }

  /** Pictogramme illustrant la consigne en cours. */
  iconFor(action: string): string {
    switch (action) {
      case 'BLINK': return 'fa-eye';
      case 'TURN_LEFT': return 'fa-arrow-left';
      case 'TURN_RIGHT': return 'fa-arrow-right';
      case 'SMILE': return 'fa-face-smile';
      default: return 'fa-face-meh';
    }
  }

  /** Consigne secondaire : dit *comment* réussir, pas seulement quoi faire. */
  hintFor(action: string): string {
    switch (action) {
      case 'BLINK': return 'Fermez franchement les yeux, puis rouvrez';
      case 'TURN_LEFT': return 'Pivotez la tête, sans bouger les épaules';
      case 'TURN_RIGHT': return 'Pivotez la tête, sans bouger les épaules';
      case 'SMILE': return 'Un sourire large, dents visibles';
      default: return 'Regardez l\'objectif, sans bouger';
    }
  }

  // ═══ Parcours ══════════════════════════════════════════════════════════

  async start(): Promise<void> {
    this.errorMessage = '';
    this.errorHint = '';
    this.phase = 'STARTING';

    try {
      await this.openCamera();
      const challenge = await this.requestChallenge();

      this.ceremonyId = challenge.ceremonyId;
      this.steps = challenge.steps;
      this.frames = [];
      this.currentIndex = -1;
      this.progress = 0;

      await this.runCountdown();

      this.phase = 'CAPTURE';
      this.startAmbient();
      this.runStep(0);

    } catch (err: any) {
      this.fail(err);
    }
  }

  private async requestChallenge(): Promise<FaceChallenge> {
    if (this.mode === 'ENROLL') {
      return firstValueFrom(this.face.enrollChallenge());
    }
    if (this.mode === 'LOGIN') {
      return firstValueFrom(this.face.loginChallenge(this.mfaToken));
    }
    return firstValueFrom(this.face.recoveryChallenge(this.mfaToken));
  }

  /**
   * Décompte avant la première consigne.
   *
   * Il sert deux buts : laisser la caméra terminer son exposition — une trame
   * prise à la première frame est souvent noire — et prévenir l'utilisateur que
   * la capture commence, pour qu'il ne rate pas la première consigne.
   */
  private runCountdown(): Promise<void> {
    this.phase = 'COUNTDOWN';
    this.countdown = 3;

    return new Promise(resolve => {
      const tick = () => {
        this.animateCountdown();
        if (this.countdown <= 0) {
          resolve();
          return;
        }
        this.schedule(() => {
          this.countdown -= 1;
          this.cdr.detectChanges();
          tick();
        }, 700);
      };
      tick();
    });
  }

  /**
   * Joue une consigne : affichage, puis rafale de trames.
   *
   * On capture plusieurs trames réparties dans la fenêtre plutôt qu'une seule à
   * la fin. Un clignement dure 100 à 400 ms : une trame unique le rate presque
   * toujours, ce qui faisait échouer la vivacité même quand l'utilisateur
   * exécutait correctement la consigne.
   */
  private runStep(index: number): void {
    if (index >= this.steps.length) {
      this.submit();
      return;
    }

    this.currentIndex = index;
    this.progress = 0;
    this.burstCount = 0;
    this.recording = false;
    const etape = this.steps[index];

    this.animateStepEntrance(etape.action);

    // L'anneau se remplit pendant la consigne : l'utilisateur voit le temps qui
    // lui reste plutôt que de subir une attente opaque.
    const compteur = { valeur: 0 };
    this.progressTween?.kill();
    this.progressTween = gsap.to(compteur, {
      valeur: 1,
      duration: etape.dureeMs / 1000,
      ease: 'none',
      onUpdate: () => {
        this.zone.run(() => { this.progress = compteur.valeur; });
      }
    });

    // La rafale occupe le temps restant une fois la lecture et la marge finale
    // retranchées. Le `max` protège d'une durée serveur trop courte, qui rendrait
    // la fenêtre négative et ferait tirer les quatre trames au même instant.
    const debut = Math.min(DELAI_LECTURE_MS, etape.dureeMs * 0.4);
    const fenetre = Math.max(400, etape.dureeMs - debut - MARGE_FIN_MS);
    const pas = fenetre / (RAFALE - 1);

    this.schedule(() => {
      this.zone.run(() => { this.recording = true; });
    }, debut);

    for (let i = 0; i < RAFALE; i++) {
      this.schedule(() => {
        this.capture(etape.action);
        this.zone.run(() => { this.burstCount = i + 1; });
      }, debut + i * pas);
    }

    this.schedule(() => {
      this.zone.run(() => { this.recording = false; });
      this.runStep(index + 1);
    }, etape.dureeMs);
  }

  /** Capture la trame courante en JPEG base64. */
  private capture(action: string): void {
    const video = this.videoRef?.nativeElement;
    const canvas = this.canvasRef?.nativeElement;
    if (!video || !canvas || !video.videoWidth) return;

    // 640 px de large suffisent largement à ArcFace et divisent le poids par
    // quatre par rapport à du 1280 — la latence réseau compte ici.
    const largeur = 640;
    const hauteur = Math.round(video.videoHeight * (largeur / video.videoWidth));
    canvas.width = largeur;
    canvas.height = hauteur;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.drawImage(video, 0, 0, largeur, hauteur);

    this.frames.push({
      action,
      image: canvas.toDataURL('image/jpeg', 0.82).split(',')[1]
    });

    this.flash();
  }

  private async submit(): Promise<void> {
    this.phase = 'ANALYZING';
    this.stopCamera();
    this.stopAmbient();
    this.animateAnalyzing();

    try {
      if (this.mode === 'ENROLL') {
        await firstValueFrom(this.face.enroll(this.ceremonyId, this.frames));
        await this.celebrate();
        this.enrolled.emit();

      } else if (this.mode === 'LOGIN') {
        const reponse = await firstValueFrom(
          this.face.loginVerify(this.mfaToken, this.ceremonyId, this.frames));
        await this.celebrate();
        if (reponse.session) {
          this.authenticated.emit(reponse.session);
        } else {
          throw new Error('Session non délivrée par le serveur');
        }

      } else {
        const reponse = await firstValueFrom(
          this.face.recoveryVerify(this.mfaToken, this.ceremonyId, this.frames));
        await this.celebrate();
        this.recovered.emit(reponse.resetToken);
      }

    } catch (err: any) {
      this.fail(err);
    }
  }

  /** Relance une cérémonie complète : les consignes sont retirées au sort. */
  retry(): void {
    this.clearTimers();
    // La caméra est fermée avant de rouvrir : réutiliser un flux dont la piste
    // a déjà été arrêtée donne un aperçu noir, sur lequel aucun visage n'est
    // détecté — l'échec se répétait alors indéfiniment.
    this.stopCamera();
    this.stopAmbient();
    this.frames = [];
    this.currentIndex = -1;
    this.progress = 0;
    this.burstCount = 0;
    this.recording = false;
    this.start();
  }

  cancel(): void {
    this.teardown();
    this.cancelled.emit();
  }

  private fail(err: any): void {
    this.clearTimers();
    this.stopCamera();
    this.stopAmbient();
    this.analyzeTimeline?.kill();
    this.phase = 'ERROR';

    const statut = err?.status;
    // 401 sur le jeton d'étape : la session de vérification a expiré, il faut
    // repartir du mot de passe — réessayer ici ne mènerait nulle part. Un échec
    // de vérification, lui, remonte en 422 et reste réessayable sur place.
    if (statut === 401 && this.mode !== 'ENROLL') {
      this.expired.emit();
      return;
    }

    this.errorMessage = err?.error?.message || this.face.describeError(err);
    this.errorHint = this.conseil(statut);
    this.animateShake();
    this.cdr.detectChanges();
  }

  /** Conseil d'action, distinct du message du serveur qui dit ce qui a échoué. */
  private conseil(statut?: number): string {
    if (statut === 503) {
      return 'Le service de reconnaissance est momentanément indisponible. Utilisez un autre facteur.';
    }
    if (statut === 429) {
      return 'Trop de tentatives. Patientez quelques minutes avant de réessayer.';
    }
    return 'Placez-vous face à une source de lumière, à environ 50 cm de l\'écran, '
      + 'et exagérez légèrement les mouvements demandés.';
  }

  // ═══ Caméra ════════════════════════════════════════════════════════════

  private async openCamera(): Promise<void> {
    if (this.stream) return;

    this.stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: 'user',
        width: { ideal: 1280 },
        height: { ideal: 720 }
      },
      audio: false
    });

    const video = this.videoRef?.nativeElement;
    if (!video) {
      this.stopCamera();
      throw new Error('Aperçu vidéo indisponible');
    }

    video.srcObject = this.stream;
    // `play()` rejette avec AbortError quand un `srcObject` précédent est
    // remplacé alors que sa lecture démarrait encore — exactement le cas au
    // « Réessayer ». Cette promesse rejetée faisait échouer la relance et
    // affichait « The play() request was interrupted » au lieu de recommencer.
    // La lecture démarre malgré tout : on attend, sans laisser l'erreur remonter.
    try {
      await video.play();
    } catch (err: any) {
      if (err?.name !== 'AbortError') throw err;
    }
    this.cameraReady = true;
  }

  private stopCamera(): void {
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
    this.cameraReady = false;
    const video = this.videoRef?.nativeElement;
    if (video) video.srcObject = null;
  }

  private teardown(): void {
    this.clearTimers();
    this.stopCamera();
    this.stopAmbient();
    this.analyzeTimeline?.kill();
    this.progressTween?.kill();
  }

  /** Les minuteries tournent hors zone : sans cela chacune relance un cycle. */
  private schedule(fn: () => void, delai: number): void {
    this.zone.runOutsideAngular(() => {
      this.timers.push(setTimeout(fn, delai));
    });
  }

  private clearTimers(): void {
    this.timers.forEach(clearTimeout);
    this.timers = [];
    this.progressTween?.kill();
  }

  // ═══ Animations ════════════════════════════════════════════════════════

  private get reduced(): boolean {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  /**
   * Deux tempéraments d'animation selon le contexte.
   *
   * À l'inscription l'utilisateur est chez lui, dans son profil : le mouvement
   * peut être ample et posé. À la connexion il attend d'entrer, souvent pressé :
   * on resserre les durées et on adoucit les rebonds, qui paraîtraient
   * frivoles sur un écran de sécurité.
   */
  private get enrolment(): boolean {
    return this.mode === 'ENROLL';
  }

  private get tempo(): number {
    return this.enrolment ? 1 : 0.78;
  }

  private get elan(): string {
    return this.enrolment ? 'back.out(1.7)' : 'power3.out';
  }

  private query(selecteur: string): Element | null {
    return this.stageRef?.nativeElement.querySelector(selecteur) ?? null;
  }

  private animateIntro(): void {
    if (this.reduced) return;
    const racine = this.stageRef?.nativeElement;
    if (!racine) return;

    // À l'inscription les anneaux éclosent en cascade ; à la connexion ils
    // apparaissent d'un bloc, plus sobrement.
    gsap.timeline()
      .from(racine.querySelectorAll('.fc-hero-layer'), {
        scale: this.enrolment ? 0.4 : 0.82,
        opacity: 0,
        duration: 0.7 * this.tempo,
        stagger: this.enrolment ? 0.09 : 0.04,
        ease: this.elan
      })
      .from(racine.querySelectorAll('.fc-intro-copy > *'), {
        y: 20, opacity: 0, duration: 0.5 * this.tempo,
        stagger: 0.07, ease: 'power3.out'
      }, '-=0.35')
      .from(racine.querySelectorAll('.fc-tips li'), {
        x: -14, opacity: 0, duration: 0.4 * this.tempo,
        stagger: 0.06, ease: 'power2.out'
      }, '-=0.3');
  }

  /** Le chiffre du décompte enfle puis s'efface : le rythme se lit sans texte. */
  private animateCountdown(): void {
    if (this.reduced) return;
    requestAnimationFrame(() => {
      const cible = this.query('.fc-countdown-value');
      if (!cible) return;
      gsap.fromTo(cible,
        { scale: 0.5, opacity: 0 },
        { scale: 1, opacity: 1, duration: 0.32, ease: 'back.out(2.2)' });
    });
  }

  /**
   * La consigne entre en rebond, et une flèche directionnelle balaie le cadre
   * pour les rotations : montrer le geste vaut mieux que le décrire.
   */
  private animateStepEntrance(action: string): void {
    if (this.reduced) return;
    requestAnimationFrame(() => {
      const consigne = this.query('.fc-instruction');
      if (consigne) {
        gsap.fromTo(consigne,
          { y: 16, opacity: 0, scale: this.enrolment ? 0.92 : 0.97 },
          { y: 0, opacity: 1, scale: 1, duration: 0.5 * this.tempo, ease: this.elan });
      }

      const guide = this.query('.fc-guide');
      if (guide && (action === 'TURN_LEFT' || action === 'TURN_RIGHT')) {
        const sens = action === 'TURN_LEFT' ? -1 : 1;
        gsap.fromTo(guide,
          { x: 0, opacity: 0 },
          {
            x: 46 * sens, opacity: 1, duration: 0.9,
            repeat: -1, yoyo: true, ease: 'sine.inOut'
          });
      } else if (guide) {
        gsap.set(guide, { opacity: 0, x: 0 });
      }
    });
  }

  /** Halo permanent pendant la capture : l'écran reste vivant entre consignes. */
  private startAmbient(): void {
    if (this.reduced) return;
    requestAnimationFrame(() => {
      const halo = this.query('.fc-halo');
      if (!halo) return;
      this.ambientTimeline?.kill();
      this.ambientTimeline = gsap.timeline({ repeat: -1, yoyo: true })
        .to(halo, { scale: 1.06, opacity: 0.85, duration: 1.6, ease: 'sine.inOut' });
    });
  }

  private stopAmbient(): void {
    this.ambientTimeline?.kill();
    this.ambientTimeline = undefined;
  }

  /** Éclair blanc bref : retour tactile confirmant la prise de vue. */
  private flash(): void {
    if (this.reduced) return;
    const cible = this.query('.fc-flash');
    if (!cible) return;
    gsap.fromTo(cible,
      { opacity: 0.5 },
      { opacity: 0, duration: 0.28, ease: 'power2.out' });
  }

  private animateAnalyzing(): void {
    if (this.reduced) return;
    requestAnimationFrame(() => {
      const points = this.stageRef?.nativeElement.querySelectorAll('.fc-dot');
      if (!points?.length) return;
      this.analyzeTimeline?.kill();
      this.analyzeTimeline = gsap.timeline({ repeat: -1 })
        .to(points, { y: -10, duration: 0.3, stagger: 0.1, ease: 'power2.out' })
        .to(points, { y: 0, duration: 0.3, stagger: 0.1, ease: 'power2.in' }, 0.3);
    });
  }

  /** La coche se trace, puis on laisse une respiration avant de rendre la main. */
  private celebrate(): Promise<void> {
    this.analyzeTimeline?.kill();
    this.phase = 'SUCCESS';
    this.cdr.detectChanges();

    if (this.reduced) {
      return new Promise(resolve => this.schedule(resolve, 300));
    }

    return new Promise(resolve => {
      requestAnimationFrame(() => {
        const coche = this.query('.fc-check-path');
        const anneau = this.query('.fc-check-ring');
        const eclat = this.query('.fc-check-burst');

        const tl = gsap.timeline({ onComplete: () => resolve() });
        if (anneau) {
          tl.fromTo(anneau, { scale: 0.6, opacity: 0 },
            { scale: 1, opacity: 1, duration: 0.4, ease: 'back.out(1.8)', transformOrigin: 'center' });
        }
        if (coche) {
          tl.fromTo(coche, { strokeDashoffset: 60 },
            { strokeDashoffset: 0, duration: 0.42, ease: 'power2.out' }, '-=0.12');
        }
        if (eclat) {
          tl.fromTo(eclat, { scale: 0.7, opacity: 0.55 },
            { scale: 1.6, opacity: 0, duration: 0.7, ease: 'power2.out', transformOrigin: 'center' }, '-=0.3');
        }
        tl.to({}, { duration: 0.45 });
      });
    });
  }

  private animateShake(): void {
    if (this.reduced) return;
    requestAnimationFrame(() => {
      const cible = this.query('.fc-error');
      if (!cible) return;
      gsap.fromTo(cible,
        { x: -10 },
        { x: 0, duration: 0.6, ease: 'elastic.out(1, 0.35)' });
    });
  }
}
