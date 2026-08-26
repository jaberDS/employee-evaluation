package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un point d'un graphique : un libellé, une valeur, une couleur.
 *
 * La couleur est choisie par le serveur dans une palette figée. La laisser au
 * modèle reviendrait à injecter une chaîne libre dans un attribut de style —
 * une valeur comme « red;background:url(…) » y trouverait sa place.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPointDTO {

    private String libelle;

    private double valeur;

    /** Couleur hexadécimale issue de la palette serveur. */
    private String couleur;
}
