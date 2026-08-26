package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.CeremonyOptionsResponse;
import com.atb.employeeevaluation.dto.CredentialSummaryDTO;
import com.atb.employeeevaluation.enums.AuthenticatorPreference;
import com.atb.employeeevaluation.enums.MfaPurpose;

import java.util.List;

public interface WebAuthnService {

    /**
     * Options d'enrôlement d'un nouvel authentificateur.
     *
     * @param preference oriente l'invite du navigateur : capteur de cet
     *                   ordinateur, ou QR code à scanner avec le téléphone.
     */
    CeremonyOptionsResponse startRegistration(String matricule, AuthenticatorPreference preference);

    /** Enregistre la clé publique produite par l'authentificateur. */
    void finishRegistration(String matricule, String ceremonyId, String label, String credentialJson);

    /**
     * Options d'assertion. Pour un {@code decoy}, un challenge réel est émis avec
     * des identifiants fabriqués : le navigateur répond « aucun passkey trouvé »,
     * indiscernable d'un mauvais appareil.
     */
    CeremonyOptionsResponse startAssertion(String matricule, MfaPurpose purpose, boolean decoy,
                                           AuthenticatorPreference preference);

    /** Vérifie l'assertion et renvoie le matricule prouvé. Lève en cas d'échec. */
    String finishAssertion(String ceremonyId, String credentialJson, MfaPurpose purpose);

    List<CredentialSummaryDTO> listCredentials(String matricule);

    void renameCredential(String matricule, Long credentialId, String label);

    void deleteCredential(String matricule, Long credentialId, String currentPassword);

    boolean hasCredentials(String matricule);
}
