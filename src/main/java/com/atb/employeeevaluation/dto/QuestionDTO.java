package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionDTO {
    private Long id;

    @NotBlank(message = "Le libellé de la question est obligatoire")
    private String libelle;

    @NotNull(message = "La note maximale est obligatoire")
    @Min(value = 1, message = "La note maximale doit être au moins 1")
    private Integer noteMax;

    @NotNull(message = "L'ordre est obligatoire")
    @Min(value = 0, message = "L'ordre doit être positif")
    private Integer ordre;

    private Long evaluationId;
}