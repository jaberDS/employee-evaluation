package com.atb.employeeevaluation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Instantané de ce que l'appelant a le droit de voir.
 *
 * C'est la pièce centrale du cloisonnement : tout ce que l'assistant sait de
 * l'utilisateur passe par ici, et rien n'y entre qui ne relève de son périmètre.
 * Un N+1 n'y trouve que les fiches de son équipe — les autres ne sont même pas
 * chargées, donc ne peuvent pas fuiter dans la consigne envoyée au modèle.
 *
 * Volontairement composé de valeurs simples, pas d'entités : il est construit
 * dans une transaction et consommé en dehors. Y laisser un proxy paresseux
 * ferait échouer la première lecture d'un nom d'employé.
 */
@Data
@Builder
public class PerimetreAssistant {

    private String matricule;
    private String role;
    private String nomComplet;
    /** AGENCE ou SIEGE. Nul pour un administrateur, qui n'est pas cantonné. */
    private String affectation;

    // ─── Synthèse ─────────────────────────────────────────────────────────────

    private long fichesTotal;
    /** Effectifs par StatutFiche, dans l'ordre du cycle de vie. */
    private Map<String, Long> fichesParStatut;
    /** Moyenne des notes finales des fiches clôturées. Nulle si aucune. */
    private Double noteMoyenne;
    private Double noteMin;
    private Double noteMax;

    private long campagnesTotal;
    private long campagnesOuvertes;

    // ─── Séries ───────────────────────────────────────────────────────────────

    /** Effectifs par tranche de note : « 0-5 », « 5-10 », « 10-15 », « 15-20 ». */
    private Map<String, Long> notesParTranche;

    /** Clôtures des six derniers mois, du plus ancien au plus récent. */
    private List<PointTemporel> tendance;

    /** Comparaison Agence / Siège. Renseignée pour l'administrateur seul. */
    private List<StatAffectation> parAffectation;

    // ─── Listes ───────────────────────────────────────────────────────────────

    private List<Campagne> campagnes;
    /** Tronquée : voir {@link #equipeTronquee}. Vide pour un employé. */
    private List<Membre> equipe;
    private boolean equipeTronquee;
    private long equipeTotal;

    /** Fiches dont l'appelant est le sujet. */
    private List<Fiche> mesFiches;

    /** Ce qui réclame son attention, calculé sans passer par le modèle. */
    private List<String> alertes;

    // ─── Structures ───────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class Campagne {
        private Long id;
        private String nom;
        private String statut;
        private String affectation;
        private String dateDebut;
        private String dateFin;
        private long fiches;
        private long cloturees;
        private int questions;
    }

    @Data
    @Builder
    public static class Membre {
        private Long id;
        private String nom;
        private String matricule;
        private String affectation;
        private long fiches;
        private long cloturees;
        private long enAttente;
        private Double noteMoyenne;
    }

    @Data
    @Builder
    public static class Fiche {
        private Long id;
        private String employe;
        private String campagne;
        private String statut;
        private Double note;
        private String date;
    }

    @Data
    @Builder
    public static class PointTemporel {
        /** Libellé court du mois, « janv. 2026 ». */
        private String mois;
        private long valeur;
    }

    @Data
    @Builder
    public static class StatAffectation {
        private String affectation;
        private long fiches;
        private long cloturees;
        private Double noteMoyenne;
    }
}
