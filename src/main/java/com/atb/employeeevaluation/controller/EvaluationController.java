package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.EvaluationDTO;
import com.atb.employeeevaluation.dto.QuestionDTO;
import com.atb.employeeevaluation.enums.TypeAffectation;
import com.atb.employeeevaluation.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    // ===================== CRUD Evaluation =====================

    @PostMapping
    public ResponseEntity<EvaluationDTO> create(@Valid @RequestBody EvaluationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.createEvaluation(dto));
    }

    /**
     * Création standard : une campagne saisie donne le couple Agence + Siège.
     * Retourne les deux campagnes créées.
     */
    @PostMapping("/paire")
    public ResponseEntity<List<EvaluationDTO>> createPaire(@Valid @RequestBody EvaluationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.createEvaluationPaire(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EvaluationDTO> update(@PathVariable Long id, @Valid @RequestBody EvaluationDTO dto) {
        return ResponseEntity.ok(evaluationService.updateEvaluation(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvaluationDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(evaluationService.getEvaluationById(id));
    }

    @GetMapping
    public ResponseEntity<List<EvaluationDTO>> getAll(
            @RequestParam(name = "type", required = false) TypeAffectation type) {
        return ResponseEntity.ok(type != null
                ? evaluationService.getEvaluationsByTypeAffectation(type)
                : evaluationService.getAllEvaluations());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        evaluationService.deleteEvaluation(id);
        return ResponseEntity.noContent().build();
    }

    // ===================== Gestion des statuts =====================

    @PatchMapping("/{id}/ouvrir")
    public ResponseEntity<Void> ouvrir(@PathVariable Long id) {
        evaluationService.ouvrirCampagne(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/fermer")
    public ResponseEntity<Void> fermer(@PathVariable Long id) {
        evaluationService.fermerCampagne(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/cloturer")
    public ResponseEntity<Void> cloturer(@PathVariable Long id) {
        evaluationService.cloturerCampagne(id);
        return ResponseEntity.ok().build();
    }

    // ===================== Gestion des questions =====================

    @PostMapping("/{evaluationId}/questions")
    public ResponseEntity<QuestionDTO> addQuestion(
            @PathVariable Long evaluationId,
            @Valid @RequestBody QuestionDTO questionDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evaluationService.addQuestion(evaluationId, questionDTO));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDTO> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionDTO questionDTO) {
        return ResponseEntity.ok(evaluationService.updateQuestion(questionId, questionDTO));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> removeQuestion(@PathVariable Long questionId) {
        evaluationService.removeQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{evaluationId}/questions")
    public ResponseEntity<List<QuestionDTO>> getQuestions(@PathVariable Long evaluationId) {
        return ResponseEntity.ok(evaluationService.getQuestionsByEvaluation(evaluationId));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDTO> getQuestionById(@PathVariable Long questionId) {
        return ResponseEntity.ok(evaluationService.getQuestionById(questionId));
    }

    @PatchMapping("/questions/{questionId}/toggle-actif")
    public ResponseEntity<QuestionDTO> toggleActif(@PathVariable Long questionId) {
        return ResponseEntity.ok(evaluationService.toggleActif(questionId));
    }
}