package com.atb.employeeevaluation.dto;

import com.atb.employeeevaluation.enums.TypeQuestion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionDTO {

    private Long id;

    @NotBlank(message = "Le libellé de la question est obligatoire")
    private String libelle;

    /** Description / consigne (optionnel) */
    private String description;

    @NotNull(message = "La note maximale est obligatoire")
    @Min(value = 1, message = "La note maximale doit être au moins 1")
    @Max(value = 10, message = "La note maximale ne peut pas dépasser 10")
    private Integer noteMax;

    @NotNull(message = "L'ordre est obligatoire")
    @Min(value = 1, message = "L'ordre doit être au moins 1")
    private Integer ordre;

    @NotNull(message = "Le type de question est obligatoire")
    private TypeQuestion typeQuestion;

    @NotNull(message = "Le champ obligatoire est requis")
    private Boolean obligatoire;

    private Boolean actif;

    private Long evaluationId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
