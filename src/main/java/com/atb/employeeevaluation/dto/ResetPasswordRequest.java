package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Pose d'un nouveau mot de passe après une récupération par passkey. */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Le jeton de réinitialisation est obligatoire")
    private String resetToken;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
            message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, "
                    + "une minuscule, un chiffre et un caractère spécial"
    )
    private String newPassword;
}
