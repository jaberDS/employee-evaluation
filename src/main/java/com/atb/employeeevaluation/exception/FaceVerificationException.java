package com.atb.employeeevaluation.exception;

/**
 * Échec d'une vérification faciale : vivacité non prouvée, visage qui ne
 * correspond pas, cérémonie déjà consommée.
 *
 * Distinct de AuthenticationFailedException, qui remonte en 401 et signifie
 * « votre session d'authentification n'est plus valable ». Ici la session
 * d'étape reste bonne : seule la tentative a échoué, et l'utilisateur doit
 * pouvoir réessayer sans repasser par le mot de passe. Un 401 déclencherait le
 * rafraîchissement de jeton côté navigateur, puis une déconnexion — d'où le 422.
 */
public class FaceVerificationException extends RuntimeException {

    public FaceVerificationException(String message) {
        super(message);
    }

    public FaceVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
