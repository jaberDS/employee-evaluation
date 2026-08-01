package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.AssistantResponse;

public interface AssistantService {

    /** Répond à une question et, s'il y a lieu, propose une destination. */
    AssistantResponse repondre(String question);

    /** Faux si la clé d'API n'est pas configurée : le client masque l'assistant. */
    boolean estDisponible();
}
