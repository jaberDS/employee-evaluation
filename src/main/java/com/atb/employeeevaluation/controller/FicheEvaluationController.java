package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.EvaluationN1Request;
import com.atb.employeeevaluation.dto.FicheEvaluationDTO;
import com.atb.employeeevaluation.dto.ValidationN2Request;
import com.atb.employeeevaluation.enums.StatutFiche;
import com.atb.employeeevaluation.service.EmployeService;
import com.atb.employeeevaluation.service.FicheEvaluationService;
import com.atb.employeeevaluation.dto.EmployeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fiches")
@RequiredArgsConstructor
public class FicheEvaluationController {

    private final FicheEvaluationService ficheService;
    private final EmployeService employeService;

    // ===================== Étape 1: N+1 évalue =====================

    @PostMapping("/evaluer")
    public ResponseEntity<FicheEvaluationDTO> evaluerParN1(
            @Valid @RequestBody EvaluationN1Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ficheService.evaluerParN1(request));
    }

    // ===================== Étape 2: N+2 valide =====================

    @PatchMapping("/{ficheId}/n2")
    public ResponseEntity<FicheEvaluationDTO> validerParN2(
            @PathVariable Long ficheId,
            @Valid @RequestBody ValidationN2Request request) {
        return ResponseEntity.ok(ficheService.validerParN2(ficheId, request));
    }

    // ===================== Étape 3: Employé valide =====================

    @PatchMapping("/{ficheId}/employe")
    public ResponseEntity<FicheEvaluationDTO> validerParEmploye(
            @PathVariable Long ficheId,
            @RequestParam boolean accepte) {
        return ResponseEntity.ok(ficheService.validerParEmploye(ficheId, accepte));
    }

    // ===================== Consultation =====================

    @GetMapping("/{id}")
    public ResponseEntity<FicheEvaluationDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ficheService.getFicheById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByEmploye(
            @PathVariable Long employeId) {
        return ResponseEntity.ok(ficheService.getFichesByEmploye(employeId));
    }

    @GetMapping("/evaluation/{evaluationId}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByEvaluation(
            @PathVariable Long evaluationId) {
        return ResponseEntity.ok(ficheService.getFichesByEvaluation(evaluationId));
    }

    @GetMapping("/statut/{statut}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByStatut(
            @PathVariable StatutFiche statut) {
        return ResponseEntity.ok(ficheService.getFichesByStatut(statut));
    }

    /** Toutes les fiches des subordonnés d'un N+1 */
    @GetMapping("/n1/{n1Id}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByN1(@PathVariable Long n1Id, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_N1".equals(a.getAuthority()))) {
            EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
            if (!n1Id.equals(current.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(ficheService.getFichesByN1(n1Id));
    }
}