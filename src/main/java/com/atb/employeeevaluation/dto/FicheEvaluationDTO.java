package com.atb.employeeevaluation.dto;

import com.atb.employeeevaluation.enums.Decision;
import com.atb.employeeevaluation.enums.StatutFiche;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class FicheEvaluationDTO {
    private Long id;
    private Long employeId;
    private String employeNom;
    private String employePrenom;
    private Long evaluationId;
    private String evaluationNom;
    private LocalDateTime dateCreation;
    private Map<Long, Integer> reponsesN1;  // Question ID -> Note
    private Double noteN1;
    private String commentaireN1;
    private Decision decisionN2;
    private String commentaireN2;
    private Decision decisionEmploye;
    private Double noteFinale;
    private StatutFiche statut;
}