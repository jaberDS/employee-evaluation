package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Finalisation de l'enrôlement d'un passkey depuis le profil. */
@Data
public class RegisterCredentialRequest {

    @NotBlank(message = "L'identifiant de cérémonie est obligatoire")
    private String ceremonyId;

    @NotBlank(message = "Le nom de l'appareil est obligatoire")
    @Size(max = 60, message = "Le nom de l'appareil ne peut pas dépasser 60 caractères")
    private String label;

    /** Réponse brute de navigator.credentials.create(). */
    @NotBlank(message = "La réponse de l'authentificateur est obligatoire")
    private String credential;
}
