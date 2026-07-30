package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Corps porteur du seul jeton d'étape MFA (demande d'options de cérémonie). */
@Data
public class MfaTokenRequest {

    @NotBlank(message = "Le jeton d'étape est obligatoire")
    private String mfaToken;
}
