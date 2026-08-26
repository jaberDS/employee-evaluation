package com.atb.employeeevaluation.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Détail complet de l'enregistrement concerné par une activité. */
@Data
public class ActiviteDetailDTO {

    private String titre;
    private String sousTitre;
    private String statut;
    private boolean entiteSupprimee;

    /** Attributs de l'enregistrement (matricule, note, dates…). */
    private List<InfoDTO> infos = new ArrayList<>();

    /** Déroulé chronologique du workflow. */
    private List<EtapeDTO> etapes = new ArrayList<>();

    /** Un attribut de l'enregistrement. */
    @Data
    public static class InfoDTO {
        private final String label;
        private final String valeur;
    }

    /** Une étape du déroulé (workflow) de l'enregistrement. */
    @Data
    public static class EtapeDTO {
        private String libelle;
        private String acteur;
        private String acteurRole;
        private LocalDateTime date;
        private Double note;
        private String commentaire;
        private String decision;
        private String icone;
        private String couleur;
    }
}
