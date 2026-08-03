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
import java.util.stream.Collectors;

/**
 * Les règles d'URL de {@code SecurityConfig} tranchent qui a le droit d'appeler
 * quel verbe ; elles ne savent rien de la ressource visée. C'est ici que se
 * vérifie la propriété : un porteur du rôle N+1 n'est le N+1 que de ses propres
 * subordonnés, et rien ne l'autorise à lire l'équipe d'un collègue.
 */
@RestController
@RequestMapping("/api/fiches")
@RequiredArgsConstructor
public class FicheEvaluationController {

    private final FicheEvaluationService ficheService;
    private final EmployeService employeService;

    // ===================== Étape 1: N+1 évalue =====================

    @PostMapping("/evaluer")
    public ResponseEntity<FicheEvaluationDTO> evaluerParN1(
            @Valid @RequestBody EvaluationN1Request request,
            Authentication authentication) {
        // Le rôle est déjà filtré par SecurityConfig ; reste à vérifier que le
        // N+1 évalue bien quelqu'un de son équipe, et non un employé quelconque.
        if (!aRole(authentication, "ADMIN")) {
            EmployeDTO moi = appelant(authentication);
            EmployeDTO cible = employeService.getEmployeById(request.getEmployeId());
            if (!moi.getId().equals(cible.getN1Id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ficheService.evaluerParN1(request));
    }

    // ===================== Étape 2: N+2 valide =====================

    @PatchMapping("/{ficheId}/n2")
    public ResponseEntity<FicheEvaluationDTO> validerParN2(
            @PathVariable Long ficheId,
            @Valid @RequestBody ValidationN2Request request,
            Authentication authentication) {
        if (!aRole(authentication, "ADMIN")) {
            EmployeDTO moi = appelant(authentication);
            EmployeDTO cible = employeService.getEmployeById(
                    ficheService.getFicheById(ficheId).getEmployeId());
            if (!moi.getId().equals(cible.getN2Id())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(ficheService.validerParN2(ficheId, request));
    }

    // ===================== Étape 3: Employé valide =====================

    @PatchMapping("/{ficheId}/employe")
    public ResponseEntity<FicheEvaluationDTO> validerParEmploye(
            @PathVariable Long ficheId,
            @RequestParam boolean accepte,
            @RequestParam(required = false) String commentaire,
            Authentication authentication) {
        // Cette étape est l'accord de l'intéressé : elle n'a de sens que si c'est
        // lui qui la pose. Un supérieur qui l'accepterait à sa place clôturerait
        // l'évaluation sans que l'employé l'ait jamais vue.
        Long proprietaire = ficheService.getFicheById(ficheId).getEmployeId();
        if (!aRole(authentication, "ADMIN")
                && !proprietaire.equals(appelant(authentication).getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ficheService.validerParEmploye(ficheId, accepte, commentaire));
    }

    // ===================== Consultation =====================

    @GetMapping("/{id}")
    public ResponseEntity<FicheEvaluationDTO> getById(@PathVariable Long id, Authentication authentication) {
        FicheEvaluationDTO fiche = ficheService.getFicheById(id);
        if (!peutVoirFicheDe(fiche.getEmployeId(), authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fiche);
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByEmploye(
            @PathVariable Long employeId, Authentication authentication) {
        if (!peutVoirFicheDe(employeId, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ficheService.getFichesByEmploye(employeId));
    }

    /**
     * Ce point ne porte aucun identifiant, ce qui le fait passer pour anodin. Il
     * renvoyait pourtant toutes les fiches de la banque à qui les demandait. La
     * liste est désormais bornée au périmètre de l'appelant.
     */
    @GetMapping("/statut/{statut}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByStatut(
            @PathVariable StatutFiche statut, Authentication authentication) {
        if (aRole(authentication, "ADMIN")) {
            return ResponseEntity.ok(ficheService.getFichesByStatut(statut));
        }
        return ResponseEntity.ok(fichesVisibles(authentication).stream()
                .filter(f -> f.getStatut() == statut)
                .collect(Collectors.toList()));
    }

    /**
     * L'identifiant de campagne est connu de tous (il s'affiche dans l'écran des
     * campagnes) ; les fiches qu'elle contient ne le sont pas.
     */
    @GetMapping("/evaluation/{evaluationId}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByEvaluation(
            @PathVariable Long evaluationId, Authentication authentication) {
        if (aRole(authentication, "ADMIN")) {
            return ResponseEntity.ok(ficheService.getFichesByEvaluation(evaluationId));
        }
        return ResponseEntity.ok(fichesVisibles(authentication).stream()
                .filter(f -> evaluationId.equals(f.getEvaluationId()))
                .collect(Collectors.toList()));
    }

    /** Toutes les fiches des subordonnés d'un N+1 */
    @GetMapping("/n1/{n1Id}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByN1(@PathVariable Long n1Id, Authentication authentication) {
        if (!estAdminOuLuiMeme(n1Id, "N1", authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ficheService.getFichesByN1(n1Id));
    }

    /** Toutes les fiches des subordonnés d'un N+2, optionnellement filtrées par statut */
    @GetMapping("/n2/{n2Id}")
    public ResponseEntity<List<FicheEvaluationDTO>> getByN2(
            @PathVariable Long n2Id,
            @RequestParam(required = false) StatutFiche statut,
            Authentication authentication) {
        if (!estAdminOuLuiMeme(n2Id, "N2", authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
        if (!estAdminOuLuiMeme(n1Id, "N1", authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ficheService.deleteAllEligibleByN1(n1Id));
    }

    // ===================== Vérifications de propriété =====================

    /** Le rôle établi par la chaîne de filtres, jamais une valeur venue du client. */
    private boolean aRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    /** L'appelant, identifié par le matricule que porte son jeton. */
    private EmployeDTO appelant(Authentication authentication) {
        return employeService.getEmployeByMatricule(authentication.getName());
    }

    /**
     * Une fiche est visible de l'ADMIN, de l'employé concerné, et des deux
     * responsables auxquels cet employé est réellement rattaché.
     *
     * La version précédente se contentait de « l'appelant n'est pas un EMPLOYE
     * donc il passe » : n'importe quel N+1 lisait les notes de toute la banque,
     * branches étrangères comprises.
     */
    private boolean peutVoirFicheDe(Long employeId, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (aRole(authentication, "ADMIN")) {
            return true;
        }
        EmployeDTO moi = appelant(authentication);
        if (employeId.equals(moi.getId())) {
            return true;
        }
        EmployeDTO cible = employeService.getEmployeById(employeId);
        if (aRole(authentication, "N1")) {
            return moi.getId().equals(cible.getN1Id());
        }
        if (aRole(authentication, "N2")) {
            return moi.getId().equals(cible.getN2Id());
        }
        return false;
    }

    /**
     * Les points en {@code /n1/{id}} et {@code /n2/{id}} désignent un responsable.
     * Seuls l'ADMIN et ce responsable lui-même y ont accès : le garde ne se
     * déclenchait auparavant que si l'appelant portait précisément ce rôle, si
     * bien qu'un EMPLOYE le traversait sans être vu.
     */
    private boolean estAdminOuLuiMeme(Long responsableId, String role, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (aRole(authentication, "ADMIN")) {
            return true;
        }
        return aRole(authentication, role) && responsableId.equals(appelant(authentication).getId());
    }

    /** Le périmètre de lecture de l'appelant, calculé depuis son seul jeton. */
    private List<FicheEvaluationDTO> fichesVisibles(Authentication authentication) {
        EmployeDTO moi = appelant(authentication);
        if (aRole(authentication, "N1")) {
            return ficheService.getFichesByN1(moi.getId());
        }
        if (aRole(authentication, "N2")) {
            return ficheService.getFichesByN2(moi.getId());
        }
        return ficheService.getFichesByEmploye(moi.getId());
    }

    /** Un N1 ne peut supprimer que les fiches de ses propres subordonnés; ADMIN passe toujours. */
    private boolean isN1OwnerOrPrivileged(Long employeId, Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_N1".equals(a.getAuthority()))) {
            return true;
        }
        EmployeDTO current = appelant(authentication);
        EmployeDTO owner = employeService.getEmployeById(employeId);
        return owner.getN1Id() != null && owner.getN1Id().equals(current.getId());
    }
}
