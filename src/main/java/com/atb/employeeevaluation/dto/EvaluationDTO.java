package com.atb.employeeevaluation.dto;

import com.atb.employeeevaluation.enums.StatutCampagne;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EvaluationDTO {
    private Long id;

    @NotBlank(message = "Le nom de l'évaluation est obligatoire")
    private String nomEvaluation;

    @NotNull(message = "La date de début est obligatoire")
    @Future(message = "La date de début doit être dans le futur")
    private LocalDateTime dateDebut;

    @NotNull(message = "La date de fin est obligatoire")
    @Future(message = "La date de fin doit être dans le futur")
    private LocalDateTime dateFin;

    private StatutCampagne statut;
    private List<QuestionDTO> questions;
}