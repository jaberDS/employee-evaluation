import {
  AfterViewInit, ChangeDetectorRef, Component, ElementRef,
  OnDestroy, OnInit, ViewChild
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import gsap from 'gsap';
import { AssistantChart, AssistantPoint, AssistantService } from '../../services/assistant.service';
import { VoiceService } from '../../services/voice.service';
import { AuthService } from '../../services/auth.service';

/** Une part de donut, prête à être posée dans un attribut SVG. */
interface Part {
  point: AssistantPoint;
  /** Longueur de l'arc et longueur restante, pour `stroke-dasharray`. */
  arc: number;
  reste: number;
  /** Décalage du départ de l'arc sur le cercle. */
  depart: number;
  pourcentage: number;
}

/** Un graphique préparé pour le gabarit : géométrie déjà calculée. */
interface Trace {
  source: AssistantChart;
  parts: Part[];
  /** Hauteur relative de chaque barre, en pourcentage du maximum. */
  hauteurs: number[];
  /** Points de la polyligne, au format « x,y x,y ». */
  polyligne: string;
  /** Repères de la courbe, pour poser un cercle à chaque mesure. */
  reperes: { x: number; y: number }[];
}

/** Un tour de conversation. */
interface Message {
  auteur: 'user' | 'ia';
  texte: string;
  /** Destination annoncée, pour afficher la pastille de redirection. */
  destination?: string | null;
  /** Graphique joint à la réponse, s'il y en a un. */
  trace?: Trace | null;
}

/**
 * Délai avant redirection **lorsque rien n'est lu à voix haute** (vocal coupé
 * ou synthèse absente) : le temps de lire la réponse à l'écran.
 *
 * Quand la réponse est prononcée, ce délai ne s'applique pas : c'est la fin de
 * la phrase qui déclenche la navigation, sinon on couperait la parole.
 */
const DELAI_NAVIGATION = 1600;

/** Garde-fou : si la synthèse ne signale jamais sa fin, on navigue quand même. */
const DELAI_SECOURS = 15_000;

/** Rayon du donut dans le repère SVG. La circonférence en découle. */
const RAYON = 42;
const CIRCONFERENCE = 2 * Math.PI * RAYON;

/** Repère de la courbe : large, peu haut — le panneau est étroit. */
const LARGEUR_COURBE = 260;
const HAUTEUR_COURBE = 90;

@Component({
  selector: 'app-assistant',
  standalone: false,
  templateUrl: './assistant.component.html',
  styleUrls: ['./assistant.component.css']
})
export class AssistantComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('panneau') panneauRef?: ElementRef<HTMLElement>;
  @ViewChild('fil') filRef?: ElementRef<HTMLElement>;
  @ViewChild('champ') champRef?: ElementRef<HTMLInputElement>;

  /** L'assistant ne s'affiche que si le serveur détient une clé. */
  disponible = false;
  ouvert = false;
  occupe = false;

  question = '';
  messages: Message[] = [];
  erreur = '';

  /** Transcription en cours de dictée, affichée en gris dans le champ. */
  apercuVocal = '';
  ecoute = false;
  /** Une réponse est en cours de lecture à voix haute. */
  parle = false;
  /** Lecture à voix haute des réponses, mémorisée d'une session à l'autre. */
  vocalActif = true;

  /** Redirection annoncée, en attente de la fin de la phrase. */
  private cheminEnAttente: string | null = null;
  private minuteurSecours: any = null;

  /** Accueil : salutation, alertes et amorces, calculées par le serveur. */
  salutation = '';
  alertes: string[] = [];
  suggestions: string[] = [];

  /** Questions de suivi proposées après la dernière réponse. */
  relances: string[] = [];

  readonly rayon = RAYON;
  readonly largeurCourbe = LARGEUR_COURBE;
  readonly hauteurCourbe = HAUTEUR_COURBE;

  private abonnements = new Subscription();

  constructor(
    private assistant: AssistantService,
    private voix: VoiceService,
    private auth: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  // ═══ Cycle de vie ══════════════════════════════════════════════════════

  ngOnInit(): void {
    this.abonnements.add(
      this.assistant.disponible().subscribe(ok => {
        this.disponible = ok;
        this.cdr.detectChanges();
      })
    );

    const memoire = localStorage.getItem('assistant-voix');
    this.vocalActif = memoire === null ? true : memoire === '1';

    this.suggestions = this.suggestionsParDefaut();
    this.brancherVoix();
  }

  ngAfterViewInit(): void {
    // Le panneau n'existe pas encore : il est monté à l'ouverture.
  }

  ngOnDestroy(): void {
    this.abonnements.unsubscribe();
    this.annulerAttente();
    this.voix.annulerEcoute();
    this.voix.arreterLecture();
  }

  private brancherVoix(): void {
    this.abonnements.add(
      this.voix.transcriptionPartielle.subscribe(texte => {
        this.apercuVocal = texte;
        this.cdr.detectChanges();
      })
    );

    // La dictée part d'elle-même : le service n'émet la transcription qu'après
    // un vrai silence (dix secondes) ou sur « Terminé », donc à ce stade la
    // phrase est bel et bien finie. Envoyer maintenant évite un clic de plus.
    this.abonnements.add(
      this.voix.transcription.subscribe(texte => {
        this.apercuVocal = '';
        this.question = this.question.trim()
          ? (this.question.trim() + ' ' + texte)
          : texte;
        this.cdr.detectChanges();
        this.envoyer();
      })
    );

    this.abonnements.add(
      this.voix.finEcoute.subscribe(() => {
        this.ecoute = false;
        this.apercuVocal = '';
        this.cdr.detectChanges();
      })
    );

    this.abonnements.add(
      this.voix.erreurs.subscribe(message => {
        this.ecoute = false;
        this.apercuVocal = '';
        this.erreur = message;
        this.cdr.detectChanges();
      })
    );

    // La navigation attend ici, et nulle part ailleurs : la phrase est finie,
    // on peut changer d'écran sans la couper.
    this.abonnements.add(
      this.voix.finLecture.subscribe(() => {
        this.parle = false;
        this.cdr.detectChanges();
        this.naviguerSiAttendu();
      })
    );
  }

  // ═══ Capacités ═════════════════════════════════════════════════════════

  get dicteeSupportee(): boolean {
    return this.voix.dicteeSupportee;
  }

  get lectureSupportee(): boolean {
    return this.voix.lectureSupportee;
  }

  // ═══ Panneau ═══════════════════════════════════════════════════════════

  basculer(): void {
    this.ouvert ? this.fermer() : this.ouvrir();
  }

  ouvrir(): void {
    this.ouvert = true;
    this.erreur = '';
    this.cdr.detectChanges();
    this.animerOuverture();
    this.chargerApercu();

    setTimeout(() => this.champRef?.nativeElement.focus(), 260);
  }

  /**
   * Alertes et amorces, rafraîchies à chaque ouverture.
   *
   * L'appel ne passe pas par le modèle : il est immédiat et sans effet sur le
   * quota, donc rien n'interdit de le rejouer aussi souvent.
   */
  private chargerApercu(): void {
    this.abonnements.add(
      this.assistant.apercu().subscribe(apercu => {
        this.salutation = apercu.salutation || '';
        this.alertes = apercu.alertes || [];
        if (apercu.suggestions?.length) {
          this.suggestions = apercu.suggestions;
        }
        this.cdr.detectChanges();
      })
    );
  }

  /**
   * Fermeture explicite : la personne reprend la main, on coupe donc la parole
   * en cours et on abandonne une redirection annoncée. C'est le seul endroit où
   * la lecture est interrompue de force.
   */
  fermer(): void {
    this.annulerAttente();
    this.voix.annulerEcoute();
    this.voix.arreterLecture();
    this.ecoute = false;
    this.parle = false;

    const panneau = this.panneauRef?.nativeElement;
    if (!panneau || this.mouvementReduit) {
      this.ouvert = false;
      return;
    }

    gsap.to(panneau, {
      opacity: 0, y: 18, scale: 0.97, duration: 0.2, ease: 'power2.in',
      onComplete: () => {
        this.ouvert = false;
        this.cdr.detectChanges();
      }
    });
  }

  /**
   * Vide le fil, ici et côté serveur.
   *
   * La mémoire de conversation vit sur le serveur : l'effacer seulement à
   * l'écran laisserait l'assistant répondre à la lumière d'un échange que
   * l'utilisateur croit avoir supprimé.
   */
  effacer(): void {
    this.messages = [];
    this.relances = [];
    this.erreur = '';
    this.annulerAttente();
    this.voix.arreterLecture();
    this.parle = false;
    this.abonnements.add(this.assistant.oublier().subscribe());
  }

  /** Coupe la lecture en cours sans fermer le panneau. */
  faireTaire(): void {
    this.voix.arreterLecture();
    this.parle = false;
    // La redirection annoncée reste due : on n'attend plus la fin de la phrase.
    this.naviguerSiAttendu();
  }

  basculerVocal(): void {
    this.vocalActif = !this.vocalActif;
    localStorage.setItem('assistant-voix', this.vocalActif ? '1' : '0');
    if (!this.vocalActif) {
      this.voix.arreterLecture();
      this.parle = false;
      this.naviguerSiAttendu();
    }
  }

  // ═══ Dictée ════════════════════════════════════════════════════════════

  /**
   * Ouvre ou ferme le micro.
   *
   * L'arrêt manuel est un « j'ai fini de parler », pas une annulation : le
   * moteur restitue ce qu'il a entendu et le texte arrive dans le champ.
   */
  basculerMicro(): void {
    if (this.ecoute) {
      this.voix.arreterEcoute();
      this.ecoute = false;
      return;
    }
    this.erreur = '';
    // La lecture en cours couvrirait le micro et se ferait réécouter.
    this.voix.arreterLecture();
    this.parle = false;
    this.voix.ecouter();
    this.ecoute = true;
    this.animerMicro();
  }

  // ═══ Conversation ══════════════════════════════════════════════════════

  utiliserSuggestion(texte: string): void {
    this.question = texte;
    this.envoyer();
  }

  envoyer(): void {
    const texte = this.question.trim();
    if (!texte || this.occupe) return;

    this.annulerAttente();
    this.messages.push({ auteur: 'user', texte });
    this.question = '';
    this.erreur = '';
    this.relances = [];
    this.occupe = true;
    this.cdr.detectChanges();
    this.defiler();

    this.abonnements.add(
      this.assistant.ask(texte).subscribe({
        next: reponse => {
          this.occupe = false;
          this.messages.push({
            auteur: 'ia',
            texte: reponse.reponse,
            destination: reponse.libelle ?? null,
            trace: this.preparer(reponse.graphique)
          });
          this.relances = reponse.relances ?? [];
          this.cdr.detectChanges();
          this.animerDernierMessage();
          this.defiler();

          // Le chemin vient du serveur, qui l'a validé contre le catalogue de
          // routes autorisées à ce rôle : rien à revérifier ici.
          this.cheminEnAttente = reponse.chemin ?? null;

          const lu = this.vocalActif && this.lectureSupportee && !!reponse.reponse?.trim();

          if (lu) {
            this.parle = true;
            this.cdr.detectChanges();
            this.voix.lire(reponse.reponse);

            // Si la synthèse reste muette — onglet en arrière-plan, voix
            // indisponible — la redirection ne doit pas rester bloquée.
            if (this.cheminEnAttente) {
              this.minuteurSecours = setTimeout(() => {
                this.parle = false;
                this.naviguerSiAttendu();
              }, DELAI_SECOURS);
            }
          } else if (this.cheminEnAttente) {
            // Sans lecture, un court temps de lecture à l'écran suffit.
            this.minuteurSecours = setTimeout(() => this.naviguerSiAttendu(), DELAI_NAVIGATION);
          }
        },
        error: err => {
          this.occupe = false;
          this.erreur = this.assistant.describeError(err);
          this.cdr.detectChanges();
        }
      })
    );
  }

  /**
   * Effectue la redirection en attente, s'il y en a une.
   *
   * Le panneau se ferme sans passer par `fermer()`, qui couperait la lecture :
   * ici elle vient précisément de se terminer, et rien ne justifie d'annuler
   * une synthèse déjà achevée.
   */
  private naviguerSiAttendu(): void {
    const chemin = this.cheminEnAttente;
    this.annulerAttente();
    if (!chemin) return;

    this.router.navigate([chemin]);
    this.ouvert = false;
    this.cdr.detectChanges();
  }

  private annulerAttente(): void {
    this.cheminEnAttente = null;
    if (this.minuteurSecours) {
      clearTimeout(this.minuteurSecours);
      this.minuteurSecours = null;
    }
  }

  private defiler(): void {
    setTimeout(() => {
      const fil = this.filRef?.nativeElement;
      if (fil) fil.scrollTop = fil.scrollHeight;
    }, 60);
  }

  /**
   * Amorces de secours, le temps que l'aperçu du serveur arrive.
   *
   * Le serveur en propose de meilleures — il connaît les données — mais le
   * panneau doit pouvoir s'ouvrir avant, et sans réseau.
   */
  private suggestionsParDefaut(): string[] {
    switch (this.auth.getRole()) {
      case 'ADMIN':
        return ['Où en sont les campagnes ?', 'Compare l\'agence et le siège', 'Quelle est la note moyenne ?'];
      case 'N1':
        return ['Où en est mon équipe ?', 'Qu\'est-ce qu\'il me reste à évaluer ?', 'Montre-moi les notes'];
      case 'N2':
        return ['Qu\'ai-je à valider ?', 'Montre-moi la répartition par statut', 'Voir les campagnes'];
      default:
        return ['Où en sont mes évaluations ?', 'Quelle est la prochaine campagne ?', 'Ouvre mon profil'];
    }
  }

  // ═══ Graphiques ════════════════════════════════════════════════════════

  /**
   * Calcule la géométrie du tracé.
   *
   * Les valeurs viennent du serveur et ne sont pas retouchées ici : ce bloc ne
   * fait que les convertir en longueurs d'arc, en hauteurs et en coordonnées.
   * Aucun chiffre affiché n'est produit par le navigateur.
   */
  private preparer(graphique?: AssistantChart | null): Trace | null {
    if (!graphique?.points?.length) return null;

    const valeurs = graphique.points.map(p => p.valeur);
    const total = valeurs.reduce((somme, v) => somme + v, 0);
    const maximum = Math.max(...valeurs);

    return {
      source: graphique,
      parts: this.parts(graphique.points, total),
      // Division par le maximum, jamais par le total : une barre représente sa
      // valeur face à la plus grande, pas sa part d'un ensemble.
      hauteurs: valeurs.map(v => (maximum > 0 ? (v / maximum) * 100 : 0)),
      polyligne: this.polyligne(valeurs, maximum),
      reperes: this.reperes(valeurs, maximum)
    };
  }

  /** Arcs du donut, chacun décalé de la somme des précédents. */
  private parts(points: AssistantPoint[], total: number): Part[] {
    let parcouru = 0;
    return points.map(point => {
      const fraction = total > 0 ? point.valeur / total : 0;
      const arc = fraction * CIRCONFERENCE;
      const part: Part = {
        point,
        arc,
        reste: CIRCONFERENCE - arc,
        // Le décalage est négatif : SVG fait reculer le tiret quand il croît.
        depart: -parcouru,
        pourcentage: Math.round(fraction * 100)
      };
      parcouru += arc;
      return part;
    });
  }

  private polyligne(valeurs: number[], maximum: number): string {
    return this.reperes(valeurs, maximum).map(p => `${p.x},${p.y}`).join(' ');
  }

  private reperes(valeurs: number[], maximum: number): { x: number; y: number }[] {
    if (!valeurs.length) return [];

    // Marge haute et basse : sans elle, un point à zéro ou au maximum se fait
    // rogner de moitié par le bord du cadre.
    const marge = 8;
    const utile = HAUTEUR_COURBE - marge * 2;
    const pas = valeurs.length > 1 ? LARGEUR_COURBE / (valeurs.length - 1) : 0;

    return valeurs.map((valeur, i) => ({
      x: Math.round((valeurs.length > 1 ? i * pas : LARGEUR_COURBE / 2) * 10) / 10,
      y: Math.round((marge + utile - (maximum > 0 ? valeur / maximum : 0) * utile) * 10) / 10
    }));
  }

  // ═══ Animations ════════════════════════════════════════════════════════

  private get mouvementReduit(): boolean {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  private animerOuverture(): void {
    if (this.mouvementReduit) return;
    requestAnimationFrame(() => {
      const panneau = this.panneauRef?.nativeElement;
      if (!panneau) return;

      gsap.fromTo(panneau,
        { opacity: 0, y: 24, scale: 0.96 },
        { opacity: 1, y: 0, scale: 1, duration: 0.42, ease: 'back.out(1.5)' });

      const entete = panneau.querySelectorAll('.ia-head > *');
      if (entete.length) {
        gsap.from(entete, { y: -10, opacity: 0, duration: 0.3, stagger: 0.05, ease: 'power2.out', delay: 0.1 });
      }

      const puces = panneau.querySelectorAll('.ia-chip');
      if (puces.length) {
        gsap.from(puces, { y: 12, opacity: 0, duration: 0.35, stagger: 0.06, ease: 'power3.out', delay: 0.14 });
      }
    });
  }

  private animerDernierMessage(): void {
    if (this.mouvementReduit) return;
    requestAnimationFrame(() => {
      const bulles = this.filRef?.nativeElement.querySelectorAll('.ia-msg');
      if (!bulles?.length) return;
      const derniere = bulles[bulles.length - 1];
      gsap.fromTo(derniere,
        { opacity: 0, y: 14, scale: 0.97 },
        { opacity: 1, y: 0, scale: 1, duration: 0.4, ease: 'back.out(1.6)' });

      // Les barres montent depuis leur base : c'est le sens de lecture d'un
      // histogramme, et cela évite qu'elles apparaissent déjà pleines.
      const barres = derniere.querySelectorAll('.ia-bar-fill');
      if (barres.length) {
        gsap.from(barres, {
          scaleY: 0, transformOrigin: 'bottom center',
          duration: 0.5, stagger: 0.05, ease: 'power3.out', delay: 0.15
        });
      }

      const arcs = derniere.querySelectorAll('.ia-donut-arc');
      if (arcs.length) {
        gsap.from(arcs, { opacity: 0, duration: 0.4, stagger: 0.07, ease: 'power2.out', delay: 0.15 });
      }

      const courbe = derniere.querySelector('.ia-line-path') as SVGPolylineElement | null;
      if (courbe) {
        // Le tracé se dessine de gauche à droite : la longueur du trait sert de
        // course à l'animation, comme pour un chemin qu'on parcourt.
        const longueur = courbe.getTotalLength?.() ?? 0;
        if (longueur) {
          gsap.fromTo(courbe,
            { strokeDasharray: longueur, strokeDashoffset: longueur },
            { strokeDashoffset: 0, duration: 0.8, ease: 'power2.out', delay: 0.15 });
        }
      }
    });
  }

  /** Onde sonore pendant l'écoute : montre que le micro capte vraiment. */
  private animerMicro(): void {
    if (this.mouvementReduit) return;
    requestAnimationFrame(() => {
      const barres = this.panneauRef?.nativeElement.querySelectorAll('.ia-wave span');
      if (!barres?.length) return;
      gsap.to(barres, {
        scaleY: 2.4,
        duration: 0.36,
        stagger: { each: 0.08, repeat: -1, yoyo: true },
        repeat: -1,
        yoyo: true,
        ease: 'sine.inOut',
        transformOrigin: 'center'
      });
    });
  }
}
