package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ValidationN2Request {
    @NotNull(message = "La décision est obligatoire")
    private Boolean accepte;

    private String commentaire;
}