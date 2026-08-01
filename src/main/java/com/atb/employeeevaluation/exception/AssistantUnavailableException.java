package com.atb.employeeevaluation.exception;

/**
 * L'assistant ne peut pas répondre : clé absente, quota épuisé, modèle
 * injoignable ou réponse illisible.
 *
 * Panne de service, pas erreur de l'utilisateur — d'où le 503. Un 4xx laisserait
 * croire que la question était fautive, et un 401 déclencherait la déconnexion
 * automatique de l'intercepteur côté navigateur.
 */
public class AssistantUnavailableException extends RuntimeException {

    public AssistantUnavailableException(String message) {
        super(message);
    }

    public AssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
