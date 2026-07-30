package com.atb.employeeevaluation.enums;

/**
 * Usage d'un jeton d'étape MFA. Il est vérifié à la validation : un jeton émis
 * pour une récupération de compte ne peut pas servir à terminer une connexion.
 */
public enum MfaPurpose {
    LOGIN_2FA,
    PASSWORD_RESET,
    ENROLLMENT
}
