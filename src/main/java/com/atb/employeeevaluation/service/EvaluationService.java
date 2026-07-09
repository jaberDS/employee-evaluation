package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.EvaluationDTO;
import com.atb.employeeevaluation.dto.QuestionDTO;

import java.util.List;

public interface EvaluationService {
    // CRUD Evaluation
    EvaluationDTO createEvaluation(EvaluationDTO dto);
    EvaluationDTO updateEvaluation(Long id, EvaluationDTO dto);
    EvaluationDTO getEvaluationById(Long id);
    List<EvaluationDTO> getAllEvaluations();
    void deleteEvaluation(Long id);

    // Gestion des statuts
    void ouvrirCampagne(Long evaluationId);
    void fermerCampagne(Long evaluationId);
    void cloturerCampagne(Long evaluationId);

    // Gestion des questions
    QuestionDTO addQuestion(Long evaluationId, QuestionDTO questionDTO);
    QuestionDTO updateQuestion(Long questionId, QuestionDTO questionDTO);
    void removeQuestion(Long questionId);
    List<QuestionDTO> getQuestionsByEvaluation(Long evaluationId);
}