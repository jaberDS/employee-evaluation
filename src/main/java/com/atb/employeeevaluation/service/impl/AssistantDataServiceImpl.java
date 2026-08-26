package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.AssistantChartDTO;
import com.atb.employeeevaluation.dto.AssistantPointDTO;
import com.atb.employeeevaluation.dto.PerimetreAssistant;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.atb.employeeevaluation.enums.AssistantChart;
import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.enums.StatutFiche;
import com.atb.employeeevaluation.enums.TypeAffectation;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import com.atb.employeeevaluation.repository.FicheEvaluationRepository;
import com.atb.employeeevaluation.service.AssistantDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Périmètre et séries de l'assistant.
 *
 * `@Transactional(readOnly = true)` n'est pas décoratif : `FicheEvaluation`
 * référence son employé et sa campagne en chargement paresseux. L'instantané est
 * bâti ici, dans la transaction, et n'expose ensuite que des valeurs simples —
 * sans quoi la première lecture d'un nom hors transaction lèverait une
 * LazyInitializationException.
 *
 * `readOnly` dit aussi ce que fait ce service : il lit. L'assistant ne doit
 * jamais écrire, et le déclarer au niveau de la transaction rend l'écart
 * impossible à commettre par inadvertance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssistantDataServiceImpl implements AssistantDataService {

    /** Bornes des listes envoyées au modèle : au-delà, le prompt enfle sans rien apprendre. */
    private static final int MAX_EQUIPE = 25;
    private static final int MAX_CAMPAGNES = 12;
    private static final int MAX_FICHES = 15;
    private static final int MOIS_TENDANCE = 6;

    /** Palette ATB, appliquée par index. Le modèle ne choisit aucune couleur. */
    private static final List<String> PALETTE = List.of(
            "#8b0000", "#c62828", "#e53935", "#ef6c60", "#f2a099", "#f7cfcb");

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeRepository employeRepository;
    private final EvaluationRepository evaluationRepository;
    private final FicheEvaluationRepository ficheRepository;

    // ═══ Périmètre ═══════════════════════════════════════════════════════════

    @Override
    public PerimetreAssistant perimetreCourant() {
        String matricule = matriculeCourant();
        String role = roleCourant();

        Optional<Employe> moi = employeRepository.findByMatricule(matricule);
        boolean admin = "ADMIN".equals(role);

        // L'administrateur voit toute la banque ; les autres sont cantonnés à
        // leur affectation, comme pour les campagnes (cf. EvaluationServiceImpl).
        TypeAffectation affectation = admin ? null : moi.map(Employe::getTypeAffectation).orElse(null);

        List<FicheEvaluation> fiches = fichesVisibles(moi.orElse(null), role);
        List<Evaluation> campagnes = campagnesVisibles(affectation);

        PerimetreAssistant.PerimetreAssistantBuilder p = PerimetreAssistant.builder()
                .matricule(matricule)
                .role(role)
                .nomComplet(moi.map(e -> e.getPrenom() + " " + e.getNom()).orElse(matricule))
                .affectation(affectation == null ? null : affectation.name());

        remplirSynthese(p, fiches);
        remplirSeries(p, fiches, admin);
        remplirCampagnes(p, campagnes, fiches);
        remplirEquipe(p, moi.orElse(null), role, fiches);
        remplirMesFiches(p, moi.orElse(null), fiches);

        PerimetreAssistant perimetre = p.build();
        perimetre.setCampagnesTotal(campagnes.size());
        perimetre.setCampagnesOuvertes(campagnes.stream()
                .filter(c -> c.getStatut() == StatutCampagne.OUVERTE).count());
        perimetre.setAlertes(alertes(perimetre, campagnes, fiches, role));
        return perimetre;
    }

    /**
     * Fiches que ce rôle a le droit de consulter.
     *
     * C'est la barrière de cloisonnement : ce qui n'est pas chargé ici ne sera
     * jamais transmis au modèle, donc jamais restitué à l'utilisateur.
     */
    private List<FicheEvaluation> fichesVisibles(Employe moi, String role) {
        if ("ADMIN".equals(role)) {
            return ficheRepository.findAll();
        }
        if (moi == null) {
            return List.of();
        }
        return switch (role) {
            case "N1" -> ficheRepository.findByEmployeN1Id(moi.getId());
            case "N2" -> ficheRepository.findByEmployeN2Id(moi.getId());
            default -> ficheRepository.findByEmployeId(moi.getId());
        };
    }

    private List<Evaluation> campagnesVisibles(TypeAffectation affectation) {
        return affectation == null
                ? evaluationRepository.findAll()
                : evaluationRepository.findByTypeAffectation(affectation);
    }

    // ═══ Agrégats ════════════════════════════════════════════════════════════

    private void remplirSynthese(PerimetreAssistant.PerimetreAssistantBuilder p,
                                 List<FicheEvaluation> fiches) {
        p.fichesTotal(fiches.size());

        // LinkedHashMap et parcours de l'enum : les statuts restent dans l'ordre
        // du cycle de vie, et ceux à zéro apparaissent — une case vide est une
        // information, sa disparition en serait une fausse.
        Map<String, Long> parStatut = new LinkedHashMap<>();
        for (StatutFiche statut : StatutFiche.values()) {
            parStatut.put(statut.name(),
                    fiches.stream().filter(f -> f.getStatut() == statut).count());
        }
        p.fichesParStatut(parStatut);

        List<Double> notes = notesCloturees(fiches);
        p.noteMoyenne(moyenne(notes));
        p.noteMin(notes.stream().min(Double::compare).orElse(null));
        p.noteMax(notes.stream().max(Double::compare).orElse(null));
    }

    private void remplirSeries(PerimetreAssistant.PerimetreAssistantBuilder p,
                               List<FicheEvaluation> fiches, boolean admin) {
        p.notesParTranche(tranches(notesCloturees(fiches)));
        p.tendance(tendance(fiches));
        p.parAffectation(admin ? parAffectation(fiches) : List.of());
    }

    /** Répartition des notes en quatre tranches de cinq points. */
    private Map<String, Long> tranches(List<Double> notes) {
        Map<String, Long> tranches = new LinkedHashMap<>();
        tranches.put("0 à 5", notes.stream().filter(n -> n < 5).count());
        tranches.put("5 à 10", notes.stream().filter(n -> n >= 5 && n < 10).count());
        tranches.put("10 à 15", notes.stream().filter(n -> n >= 10 && n < 15).count());
        tranches.put("15 à 20", notes.stream().filter(n -> n >= 15).count());
        return tranches;
    }

    /**
     * Clôtures des six derniers mois.
     *
     * Les mois sans clôture sont conservés à zéro : les omettre écraserait
     * l'échelle du temps et ferait lire une reprise là où il y a un creux.
     */
    private List<PerimetreAssistant.PointTemporel> tendance(List<FicheEvaluation> fiches) {
        LocalDate debut = LocalDate.now().withDayOfMonth(1).minusMonths(MOIS_TENDANCE - 1L);

        List<PerimetreAssistant.PointTemporel> serie = new ArrayList<>();
        for (int i = 0; i < MOIS_TENDANCE; i++) {
            LocalDate mois = debut.plusMonths(i);
            long compte = fiches.stream()
                    .filter(f -> f.getStatut() == StatutFiche.CLOTUREE)
                    .filter(f -> f.getDateCreation() != null)
                    .filter(f -> memeMois(f.getDateCreation(), mois))
                    .count();
            serie.add(PerimetreAssistant.PointTemporel.builder()
                    .mois(mois.getMonth().getDisplayName(TextStyle.SHORT, Locale.FRENCH)
                            + " " + mois.getYear())
                    .valeur(compte)
                    .build());
        }
        return serie;
    }

    private boolean memeMois(LocalDateTime date, LocalDate mois) {
        return date.getYear() == mois.getYear() && date.getMonthValue() == mois.getMonthValue();
    }

    private List<PerimetreAssistant.StatAffectation> parAffectation(List<FicheEvaluation> fiches) {
        List<PerimetreAssistant.StatAffectation> stats = new ArrayList<>();
        for (TypeAffectation type : TypeAffectation.values()) {
            List<FicheEvaluation> lot = fiches.stream()
                    .filter(f -> f.getEmploye() != null && f.getEmploye().getTypeAffectation() == type)
                    .toList();
            stats.add(PerimetreAssistant.StatAffectation.builder()
                    .affectation(type.name())
                    .fiches(lot.size())
                    .cloturees(lot.stream().filter(f -> f.getStatut() == StatutFiche.CLOTUREE).count())
                    .noteMoyenne(moyenne(notesCloturees(lot)))
                    .build());
        }
        return stats;
    }

    // ═══ Listes ══════════════════════════════════════════════════════════════

    private void remplirCampagnes(PerimetreAssistant.PerimetreAssistantBuilder p,
                                  List<Evaluation> campagnes, List<FicheEvaluation> fiches) {
        // Les fiches sont regroupées une fois pour toutes : les compter campagne
        // par campagne relancerait une requête à chaque tour de boucle.
        Map<Long, List<FicheEvaluation>> parCampagne = fiches.stream()
                .filter(f -> f.getEvaluation() != null)
                .collect(Collectors.groupingBy(f -> f.getEvaluation().getId()));

        p.campagnes(campagnes.stream()
                .sorted(Comparator.comparing(Evaluation::getDateDebut).reversed())
                .limit(MAX_CAMPAGNES)
                .map(c -> {
                    List<FicheEvaluation> lot = parCampagne.getOrDefault(c.getId(), List.of());
                    return PerimetreAssistant.Campagne.builder()
                            .id(c.getId())
                            .nom(c.getNomEvaluation())
                            .statut(c.getStatut().name())
                            .affectation(c.getTypeAffectation().name())
                            .dateDebut(c.getDateDebut().toLocalDate().format(JOUR))
                            .dateFin(c.getDateFin().toLocalDate().format(JOUR))
                            .fiches(lot.size())
                            .cloturees(lot.stream().filter(f -> f.getStatut() == StatutFiche.CLOTUREE).count())
                            .questions(c.getQuestions().size())
                            .build();
                })
                .toList());
    }

    /**
     * Équipe encadrée par l'appelant. Vide pour un employé, qui n'encadre
     * personne — et à qui la liste du personnel n'a pas à être servie.
     */
    private void remplirEquipe(PerimetreAssistant.PerimetreAssistantBuilder p,
                               Employe moi, String role, List<FicheEvaluation> fiches) {
        List<Employe> membres = switch (role) {
            case "ADMIN" -> employeRepository.findAll();
            case "N1" -> moi == null ? List.<Employe>of() : employeRepository.findByN1Id(moi.getId());
            case "N2" -> moi == null ? List.<Employe>of() : employeRepository.findByN2Id(moi.getId());
            default -> List.<Employe>of();
        };

        Map<Long, List<FicheEvaluation>> parEmploye = fiches.stream()
                .filter(f -> f.getEmploye() != null)
                .collect(Collectors.groupingBy(f -> f.getEmploye().getId()));

        p.equipeTotal(membres.size());
        p.equipeTronquee(membres.size() > MAX_EQUIPE);
        p.equipe(membres.stream()
                .limit(MAX_EQUIPE)
                .map(e -> {
                    List<FicheEvaluation> lot = parEmploye.getOrDefault(e.getId(), List.of());
                    return PerimetreAssistant.Membre.builder()
                            .id(e.getId())
                            .nom(e.getPrenom() + " " + e.getNom())
                            .matricule(e.getMatricule())
                            .affectation(e.getTypeAffectation().name())
                            .fiches(lot.size())
                            .cloturees(lot.stream().filter(f -> f.getStatut() == StatutFiche.CLOTUREE).count())
                            .enAttente(lot.stream().filter(f -> f.getStatut() != StatutFiche.CLOTUREE).count())
                            .noteMoyenne(moyenne(notesCloturees(lot)))
                            .build();
                })
                .toList());
    }

    /** Fiches dont l'appelant est lui-même le sujet, tous rôles confondus. */
    private void remplirMesFiches(PerimetreAssistant.PerimetreAssistantBuilder p,
                                  Employe moi, List<FicheEvaluation> fiches) {
        if (moi == null) {
            p.mesFiches(List.of());
            return;
        }
        p.mesFiches(fiches.stream()
                .filter(f -> f.getEmploye() != null && moi.getId().equals(f.getEmploye().getId()))
                .sorted(Comparator.comparing(FicheEvaluation::getDateCreation,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_FICHES)
                .map(this::versFiche)
                .toList());
    }

    private PerimetreAssistant.Fiche versFiche(FicheEvaluation f) {
        return PerimetreAssistant.Fiche.builder()
                .id(f.getId())
                .employe(f.getEmploye() == null ? null
                        : f.getEmploye().getPrenom() + " " + f.getEmploye().getNom())
                .campagne(f.getEvaluation() == null ? null : f.getEvaluation().getNomEvaluation())
                .statut(f.getStatut().name())
                .note(f.getNoteFinale() != null ? f.getNoteFinale() : f.getNoteN1())
                .date(f.getDateCreation() == null ? null : f.getDateCreation().toLocalDate().format(JOUR))
                .build();
    }

    // ═══ Alertes ═════════════════════════════════════════════════════════════

    /**
     * Ce qui attend l'utilisateur, calculé sans passer par le modèle : c'est
     * instantané, gratuit, et hors quota. Trois lignes au plus — une liste plus
     * longue cesse d'être une alerte.
     */
    private List<String> alertes(PerimetreAssistant p, List<Evaluation> campagnes,
                                 List<FicheEvaluation> fiches, String role) {
        List<String> alertes = new ArrayList<>();
        Map<String, Long> statuts = p.getFichesParStatut();

        switch (role) {
            case "N1" -> {
                long aSaisir = statuts.getOrDefault(StatutFiche.EN_ATTENTE.name(), 0L)
                        + statuts.getOrDefault(StatutFiche.EN_COURS_N1.name(), 0L);
                if (aSaisir > 0) {
                    alertes.add(aSaisir + (aSaisir > 1
                            ? " fiches attendent votre saisie"
                            : " fiche attend votre saisie"));
                }
                long aReviser = statuts.getOrDefault(StatutFiche.A_REVISER.name(), 0L);
                if (aReviser > 0) {
                    alertes.add(aReviser + (aReviser > 1
                            ? " évaluations sont à réviser"
                            : " évaluation est à réviser"));
                }
            }
            case "N2" -> {
                long aValider = statuts.getOrDefault(StatutFiche.EN_ATTENTE_N2.name(), 0L);
                if (aValider > 0) {
                    alertes.add(aValider + (aValider > 1
                            ? " évaluations attendent votre validation"
                            : " évaluation attend votre validation"));
                }
            }
            case "ADMIN" -> {
                long sansQuestion = campagnes.stream()
                        .filter(c -> c.getStatut() == StatutCampagne.BROUILLON)
                        .filter(c -> c.getQuestions().isEmpty())
                        .count();
                if (sansQuestion > 0) {
                    alertes.add(sansQuestion + (sansQuestion > 1
                            ? " campagnes en brouillon n'ont aucune question"
                            : " campagne en brouillon n'a aucune question"));
                }
                long enSouffrance = statuts.getOrDefault(StatutFiche.EN_ATTENTE_N2.name(), 0L);
                if (enSouffrance > 0) {
                    alertes.add(enSouffrance + " évaluations sont en attente de validation N+2");
                }
            }
            default -> {
                long aRepondre = fiches.stream()
                        .filter(f -> f.getStatut() == StatutFiche.EN_ATTENTE_EMPLOYE)
                        .count();
                if (aRepondre > 0) {
                    alertes.add(aRepondre > 1
                            ? aRepondre + " évaluations attendent votre réponse"
                            : "Votre évaluation attend votre réponse");
                }
            }
        }

        // Une campagne qui ferme sous huit jours concerne tout le monde.
        LocalDateTime bientot = LocalDateTime.now().plusDays(8);
        campagnes.stream()
                .filter(c -> c.getStatut() == StatutCampagne.OUVERTE)
                .filter(c -> c.getDateFin().isAfter(LocalDateTime.now()) && c.getDateFin().isBefore(bientot))
                .findFirst()
                .ifPresent(c -> alertes.add("La campagne « " + c.getNomEvaluation()
                        + " » se termine le " + c.getDateFin().toLocalDate().format(JOUR)));

        return alertes.stream().limit(3).toList();
    }

    // ═══ Graphiques ══════════════════════════════════════════════════════════

    @Override
    public Optional<AssistantChartDTO> tracer(AssistantChart graphique, PerimetreAssistant p) {
        if (graphique == null || p == null) {
            return Optional.empty();
        }

        List<AssistantPointDTO> points = switch (graphique) {
            case STATUTS_DONUT -> depuisCarte(p.getFichesParStatut(), this::libelleStatut);
            case NOTES_HISTOGRAMME -> depuisCarte(p.getNotesParTranche(), Function.identity());
            case TENDANCE_LIGNE -> indexer(p.getTendance().stream()
                    .map(t -> point(t.getMois(), t.getValeur()))
                    .toList());
            case EQUIPE_BARRES -> indexer(p.getEquipe().stream()
                    .map(m -> point(m.getNom(), m.getCloturees()))
                    .toList());
            case CAMPAGNES_PROGRESSION -> indexer(p.getCampagnes().stream()
                    .map(c -> point(c.getNom(), c.getFiches() == 0
                            ? 0
                            : Math.round(c.getCloturees() * 1000.0 / c.getFiches()) / 10.0))
                    .toList());
            case AFFECTATION_COMPARAISON -> indexer(p.getParAffectation().stream()
                    .map(a -> point(libelleAffectation(a.getAffectation()), a.getCloturees()))
                    .toList());
        };

        // Une série vide ou entièrement nulle ne dessine rien : mieux vaut ne
        // rien montrer qu'un cadre vide qui se lit comme un composant en panne.
        if (points.isEmpty() || points.stream().allMatch(pt -> pt.getValeur() == 0)) {
            return Optional.empty();
        }

        return Optional.of(AssistantChartDTO.builder()
                .cle(graphique.name())
                .type(graphique.getType())
                .titre(titre(graphique))
                .soustitre(soustitre(graphique, p))
                .points(points)
                .build());
    }

    private List<AssistantPointDTO> depuisCarte(Map<String, Long> valeurs,
                                                Function<String, String> libelle) {
        if (valeurs == null) {
            return List.of();
        }
        return indexer(valeurs.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> point(libelle.apply(e.getKey()), e.getValue()))
                .toList());
    }

    /** Applique la palette dans l'ordre : la couleur ne vient jamais du modèle. */
    private List<AssistantPointDTO> indexer(List<AssistantPointDTO> points) {
        List<AssistantPointDTO> colores = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            AssistantPointDTO pt = points.get(i);
            pt.setCouleur(PALETTE.get(i % PALETTE.size()));
            colores.add(pt);
        }
        return colores;
    }

    private AssistantPointDTO point(String libelle, double valeur) {
        return AssistantPointDTO.builder().libelle(libelle).valeur(valeur).build();
    }

    private String titre(AssistantChart graphique) {
        return switch (graphique) {
            case STATUTS_DONUT -> "Répartition par statut";
            case NOTES_HISTOGRAMME -> "Distribution des notes";
            case TENDANCE_LIGNE -> "Évaluations clôturées";
            case EQUIPE_BARRES -> "Évaluations clôturées par personne";
            case CAMPAGNES_PROGRESSION -> "Avancement des campagnes";
            case AFFECTATION_COMPARAISON -> "Agence / Siège";
        };
    }

    private String soustitre(AssistantChart graphique, PerimetreAssistant p) {
        String cadre = p.getAffectation() == null
                ? "Ensemble de la banque"
                : libelleAffectation(p.getAffectation());
        return switch (graphique) {
            case TENDANCE_LIGNE -> cadre + " — " + MOIS_TENDANCE + " derniers mois";
            case CAMPAGNES_PROGRESSION -> cadre + " — en pourcentage de fiches clôturées";
            case NOTES_HISTOGRAMME -> cadre + " — notes finales sur 20";
            default -> cadre;
        };
    }

    private String libelleStatut(String statut) {
        return switch (statut) {
            case "EN_ATTENTE" -> "En attente";
            case "EN_COURS_N1" -> "En cours N+1";
            case "EN_ATTENTE_N2" -> "En attente N+2";
            case "EN_ATTENTE_EMPLOYE" -> "En attente employé";
            case "CLOTUREE" -> "Clôturée";
            case "A_REVISER" -> "À réviser";
            default -> statut;
        };
    }

    private String libelleAffectation(String affectation) {
        return "AGENCE".equals(affectation) ? "Agence" : "Siège";
    }

    // ═══ Utilitaires ═════════════════════════════════════════════════════════

    private List<Double> notesCloturees(List<FicheEvaluation> fiches) {
        return fiches.stream()
                .filter(f -> f.getStatut() == StatutFiche.CLOTUREE && f.getNoteFinale() != null)
                .map(FicheEvaluation::getNoteFinale)
                .toList();
    }

    private Double moyenne(List<Double> valeurs) {
        return valeurs.isEmpty() ? null
                : Math.round(valeurs.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100.0) / 100.0;
    }

    // ═══ Contexte de sécurité ════════════════════════════════════════════════

    private String matriculeCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "inconnu";
    }

    /** Rôle issu du jeton signé, jamais du corps de la requête. */
    private String roleCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "EMPLOYE";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("EMPLOYE");
    }
}
