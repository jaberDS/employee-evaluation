package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EvaluationN1Request {
    @NotNull(message = "L'ID de l'employé est obligatoire")
    private Long employeId;

    @NotNull(message = "L'ID de la campagne est obligatoire")
    private Long evaluationId;

    @NotNull(message = "Les réponses sont obligatoires")
    private Map<Long, Double> reponses;  // Question ID -> Note

    private String commentaire;
}