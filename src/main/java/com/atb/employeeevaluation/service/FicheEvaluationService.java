package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.EvaluationN1Request;
import com.atb.employeeevaluation.dto.FicheEvaluationDTO;
import com.atb.employeeevaluation.dto.ValidationN2Request;
import com.atb.employeeevaluation.enums.StatutFiche;

import java.util.List;

public interface FicheEvaluationService {

    // Étape 1: N+1 évalue l'employé
    FicheEvaluationDTO evaluerParN1(EvaluationN1Request request);

    // Étape 2: N+2 valide ou refuse l'évaluation
    FicheEvaluationDTO validerParN2(Long ficheId, ValidationN2Request request);

    // Étape 3: Employé valide ou refuse son évaluation
    FicheEvaluationDTO validerParEmploye(Long ficheId, boolean accepte);

    // Consultation
    FicheEvaluationDTO getFicheById(Long id);
    List<FicheEvaluationDTO> getFichesByEmploye(Long employeId);
    List<FicheEvaluationDTO> getFichesByEvaluation(Long evaluationId);
    List<FicheEvaluationDTO> getFichesByStatut(StatutFiche statut);
    List<FicheEvaluationDTO> getFichesByN1(Long n1Id);

    // Utilitaires
    void recalculerNoteFinale(Long ficheId);
}