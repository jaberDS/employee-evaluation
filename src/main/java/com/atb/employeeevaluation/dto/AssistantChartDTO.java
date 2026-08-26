package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Un jeu de données prêt à être tracé.
 *
 * Les valeurs sont **calculées par le serveur**, jamais reprises de la sortie du
 * modèle : celui-ci choisit le graphique à montrer, pas ce qu'il raconte. Un
 * modèle qui hallucine ne peut donc pas afficher un chiffre faux à l'écran.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantChartDTO {

    /** Clé du catalogue retenue, à titre informatif. */
    private String cle;

    /** Forme de rendu attendue côté client : DONUT, BARRES ou LIGNE. */
    private String type;

    /** Titre affiché au-dessus du tracé. */
    private String titre;

    /** Précision sous le titre — période couverte, unité, périmètre. */
    private String soustitre;

    private List<AssistantPointDTO> points;
}
