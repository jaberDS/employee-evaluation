import {
  AfterViewInit, ChangeDetectorRef, Component, ElementRef,
  OnDestroy, OnInit, ViewChild
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import gsap from 'gsap';
import { AssistantService } from '../../services/assistant.service';
import { VoiceService } from '../../services/voice.service';
import { AuthService } from '../../services/auth.service';

/** Un tour de conversation. */
interface Message {
  auteur: 'user' | 'ia';
  texte: string;
  /** Destination annoncée, pour afficher la pastille de redirection. */
  destination?: string | null;
}

/** Délai avant redirection : laisse le temps de lire la réponse. */
const DELAI_NAVIGATION = 1300;

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
  /** Lecture à voix haute des réponses, mémorisée d'une session à l'autre. */
  vocalActif = true;

  /** Exemples proposés tant que la conversation est vide. */
  suggestions: string[] = [];

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

    this.suggestions = this.suggestionsPourRole();
    this.brancherVoix();
  }

  ngAfterViewInit(): void {
    // Le panneau n'existe pas encore : il est monté à l'ouverture.
  }

  ngOnDestroy(): void {
    this.abonnements.unsubscribe();
    this.voix.arreterEcoute();
    this.voix.arreterLecture();
  }

  private brancherVoix(): void {
    this.abonnements.add(
      this.voix.transcriptionPartielle.subscribe(texte => {
        this.apercuVocal = texte;
        this.cdr.detectChanges();
      })
    );

    // Une phrase dictée part directement : redemander de cliquer « envoyer »
    // annulerait tout le bénéfice de la dictée.
    this.abonnements.add(
      this.voix.transcription.subscribe(texte => {
        this.apercuVocal = '';
        this.question = texte;
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

    setTimeout(() => this.champRef?.nativeElement.focus(), 260);
  }

  fermer(): void {
    this.voix.arreterEcoute();
    this.voix.arreterLecture();
    this.ecoute = false;

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

  effacer(): void {
    this.messages = [];
    this.erreur = '';
    this.voix.arreterLecture();
  }

  basculerVocal(): void {
    this.vocalActif = !this.vocalActif;
    localStorage.setItem('assistant-voix', this.vocalActif ? '1' : '0');
    if (!this.vocalActif) {
      this.voix.arreterLecture();
    }
  }

  // ═══ Dictée ════════════════════════════════════════════════════════════

  basculerMicro(): void {
    if (this.ecoute) {
      this.voix.arreterEcoute();
      this.ecoute = false;
      return;
    }
    this.erreur = '';
    // La lecture en cours couvrirait le micro et se ferait réécouter.
    this.voix.arreterLecture();
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

    this.messages.push({ auteur: 'user', texte });
    this.question = '';
    this.erreur = '';
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
            destination: reponse.libelle ?? null
          });
          this.cdr.detectChanges();
          this.animerDernierMessage();
          this.defiler();

          if (this.vocalActif) {
            this.voix.lire(reponse.reponse);
          }

          // Le chemin vient du serveur, qui l'a validé contre le catalogue de
          // routes autorisées à ce rôle : rien à revérifier ici.
          if (reponse.chemin) {
            setTimeout(() => {
              this.router.navigate([reponse.chemin!]);
              this.fermer();
            }, DELAI_NAVIGATION);
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

  private defiler(): void {
    setTimeout(() => {
      const fil = this.filRef?.nativeElement;
      if (fil) fil.scrollTop = fil.scrollHeight;
    }, 60);
  }

  /** Amorces adaptées au rôle : ce que la personne peut réellement demander. */
  private suggestionsPourRole(): string[] {
    switch (this.auth.getRole()) {
      case 'ADMIN':
        return ['Combien d\'employés actifs ?', 'Ouvre la liste des employés', 'Quelle est la note moyenne ?'];
      case 'N1':
        return ['Évaluer mon équipe', 'Combien d\'évaluations en attente ?', 'Voir mon historique'];
      case 'N2':
        return ['Les évaluations à valider', 'Quelle est la note moyenne ?', 'Voir les campagnes'];
      default:
        return ['Mes évaluations', 'Quelle est la prochaine campagne ?', 'Ouvre mon profil'];
    }
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
      gsap.fromTo(bulles[bulles.length - 1],
        { opacity: 0, y: 14, scale: 0.97 },
        { opacity: 1, y: 0, scale: 1, duration: 0.4, ease: 'back.out(1.6)' });
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
