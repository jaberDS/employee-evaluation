package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.CeremonyOptionsResponse;
import com.atb.employeeevaluation.dto.FaceChallengeResponse;
import com.atb.employeeevaluation.dto.FaceVerifyRequest;
import com.atb.employeeevaluation.dto.LoginResponse;
import com.atb.employeeevaluation.dto.MfaVerifyRequest;
import com.atb.employeeevaluation.dto.ResetPasswordRequest;
import com.atb.employeeevaluation.enums.AuthenticatorPreference;

public interface MfaService {

    /** Options d'assertion pour terminer une connexion. */
    CeremonyOptionsResponse startLoginAssertion(String mfaToken, AuthenticatorPreference preference);

    /** Valide le second facteur et délivre la session. */
    LoginResponse finishLoginAssertion(MfaVerifyRequest request);

    /** Consignes de vivacité pour terminer une connexion par reconnaissance faciale. */
    FaceChallengeResponse startLoginFace(String mfaToken);

    /** Valide le visage et délivre la session. */
    LoginResponse finishLoginFace(FaceVerifyRequest request);

    /**
     * Démarre une récupération. Répond de façon identique que le matricule
     * existe ou non — la réponse ne doit rien révéler.
     */
    LoginResponse startRecovery(String matricule, String clientIp);

    CeremonyOptionsResponse startRecoveryAssertion(String mfaToken, AuthenticatorPreference preference);

    /** Valide la clé et renvoie un jeton n'autorisant que la pose d'un mot de passe. */
    String finishRecoveryAssertion(MfaVerifyRequest request);

    FaceChallengeResponse startRecoveryFace(String mfaToken);

    /** Valide le visage et renvoie un jeton n'autorisant que la pose d'un mot de passe. */
    String finishRecoveryFace(FaceVerifyRequest request);

    void resetPassword(ResetPasswordRequest request);
}
