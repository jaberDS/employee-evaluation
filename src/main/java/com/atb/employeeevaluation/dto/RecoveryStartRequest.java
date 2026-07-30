package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Démarrage d'une récupération de compte. */
@Data
public class RecoveryStartRequest {

    @NotBlank(message = "Le matricule est obligatoire")
    private String matricule;
}
