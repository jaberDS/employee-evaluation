package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ce qui attend l'utilisateur, présenté à l'ouverture du panneau.
 *
 * Entièrement calculé par le serveur : aucun appel au modèle, donc immédiat,
 * gratuit et sans effet sur le quota. C'est ce qui permet de l'afficher à
 * chaque ouverture sans y réfléchir.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantApercuDTO {

    /** Salutation nominative. */
    private String salutation;

    /** Zéro à trois lignes d'alerte. */
    private List<String> alertes;

    /** Amorces de question adaptées au rôle. */
    private List<String> suggestions;
}
