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
            @RequestParam boolean accepte,
            @RequestParam(required = false) String commentaire,
            Authentication authentication) {
        if (!isOwnerOrPrivileged(ficheService.getFicheById(ficheId).getEmployeId(), authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ficheService.validerParEmploye(ficheId, accepte, commentaire));
    }

    // ===================== Consultation =====================

    @GetMapping("/{id}")
    public ResponseEntity<FicheEvaluationDTO> getById(@PathVariable Long id, Authentication authentication) {
        FicheEvaluationDTO fiche = ficheService.getFicheById(id);
        if (!isOwnerOrPrivileged(fiche.getEmployeId(), authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fiche);
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByEmploye(
            @PathVariable Long employeId, Authentication authentication) {
        if (!isOwnerOrPrivileged(employeId, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ficheService.getFichesByEmploye(employeId));
    }

    /** Un EMPLOYE ne peut agir/consulter que ses propres fiches; les autres rôles authentifiés passent. */
    private boolean isOwnerOrPrivileged(Long employeId, Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_EMPLOYE".equals(a.getAuthority()))) {
            return true;
        }
        EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
        return employeId.equals(current.getId());
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

    /** Toutes les fiches des subordonnés d'un N+2, optionnellement filtrées par statut */
    @GetMapping("/n2/{n2Id}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByN2(
            @PathVariable Long n2Id,
            @RequestParam(required = false) StatutFiche statut,
            Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_N2".equals(a.getAuthority()))) {
            EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
            if (!n2Id.equals(current.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(statut == null
                ? ficheService.getFichesByN2(n2Id)
                : ficheService.getFichesByN2AndStatut(n2Id, statut));
    }

    // ===================== Suppression (fiches clôturées et confirmées) =====================

    @DeleteMapping("/{ficheId}")
    public ResponseEntity<Void> deleteFiche(@PathVariable Long ficheId, Authentication authentication) {
        FicheEvaluationDTO fiche = ficheService.getFicheById(ficheId);
        if (!isN1OwnerOrPrivileged(fiche.getEmployeId(), authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ficheService.deleteFiche(ficheId);
        return ResponseEntity.noContent().build();
    }

    /** Supprime en masse toutes les fiches éligibles (clôturées, campagne clôturée, N+2 et employé confirmatifs). */
    @DeleteMapping("/n1/{n1Id}")
    public ResponseEntity<Integer> deleteAllEligibleByN1(@PathVariable Long n1Id, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_N1".equals(a.getAuthority()))) {
            EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
            if (!n1Id.equals(current.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(ficheService.deleteAllEligibleByN1(n1Id));
    }

    /** Un N1 ne peut supprimer que les fiches de ses propres subordonnés; ADMIN passe toujours. */
    private boolean isN1OwnerOrPrivileged(Long employeId, Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_N1".equals(a.getAuthority()))) {
            return true;
        }
        EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
        EmployeDTO owner = employeService.getEmployeById(employeId);
        return owner.getN1Id() != null && owner.getN1Id().equals(current.getId());
    }
}