package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.AssistantApercuDTO;
import com.atb.employeeevaluation.dto.AssistantResponse;

public interface AssistantService {

    /** Répond à une question et, s'il y a lieu, propose une destination. */
    AssistantResponse repondre(String question);

    /** Ce qui attend l'utilisateur à l'ouverture — sans appel au modèle. */
    AssistantApercuDTO apercu();

    /** Efface la conversation mémorisée pour l'utilisateur courant. */
    void oublier();

    /** Faux si la clé d'API n'est pas configurée : le client masque l'assistant. */
    boolean estDisponible();
}
