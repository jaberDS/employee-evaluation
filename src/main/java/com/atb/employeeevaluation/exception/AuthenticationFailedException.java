package com.atb.employeeevaluation.exception;

/**
 * Échec d'une preuve d'authentification (assertion WebAuthn invalide, jeton
 * d'étape expiré). Distinct de UnauthorizedOperationException, qui remonte en
 * 403 : ici la bonne réponse est 401.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
