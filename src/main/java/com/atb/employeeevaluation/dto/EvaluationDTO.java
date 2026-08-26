package com.atb.employeeevaluation.dto;

import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.enums.TypeAffectation;
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

    /**
     * Pas de contrainte @Future : les champs datetime-local ont une précision
     * à la minute, donc choisir « maintenant » produit un instant déjà passé
     * au moment où la requête arrive. La cohérence début < fin est vérifiée
     * dans le service.
     */
    @NotNull(message = "La date de début est obligatoire")
    private LocalDateTime dateDebut;

    @NotNull(message = "La date de fin est obligatoire")
    @Future(message = "La date de fin doit être dans le futur")
    private LocalDateTime dateFin;

    private StatutCampagne statut;

    /**
     * Population ciblée. Affectée par le serveur lors de la création du couple
     * de campagnes (Agence + Siège) — non fournie par le client.
     */
    private TypeAffectation typeAffectation;

    private List<QuestionDTO> questions;
}