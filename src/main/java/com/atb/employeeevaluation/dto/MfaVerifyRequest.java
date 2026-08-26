package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Finalisation d'une cérémonie WebAuthn, en connexion comme en récupération. */
@Data
public class MfaVerifyRequest {

    @NotBlank(message = "Le jeton d'étape est obligatoire")
    private String mfaToken;

    @NotBlank(message = "L'identifiant de cérémonie est obligatoire")
    private String ceremonyId;

    /** Réponse brute de navigator.credentials.get(), sérialisée par le client. */
    @NotBlank(message = "La réponse de l'authentificateur est obligatoire")
    private String credential;
}
