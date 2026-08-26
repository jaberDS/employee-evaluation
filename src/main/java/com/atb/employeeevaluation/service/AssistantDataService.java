package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.AssistantChartDTO;
import com.atb.employeeevaluation.dto.PerimetreAssistant;
import com.atb.employeeevaluation.enums.AssistantChart;

import java.util.Optional;

/**
 * Calcule ce que l'assistant a le droit de savoir sur l'appelant.
 *
 * Tout part du matricule lu dans le jeton : aucune méthode ne prend en
 * paramètre l'identité de la personne à observer, précisément pour qu'aucun
 * appelant ne puisse demander le périmètre de quelqu'un d'autre.
 */
public interface AssistantDataService {

    /** Instantané complet du périmètre de l'utilisateur courant. */
    PerimetreAssistant perimetreCourant();

    /** Série d'un graphique, calculée depuis un périmètre déjà chargé. */
    Optional<AssistantChartDTO> tracer(AssistantChart graphique, PerimetreAssistant perimetre);
}
