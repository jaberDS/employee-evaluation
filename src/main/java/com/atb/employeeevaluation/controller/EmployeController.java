package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.enums.TypeAffectation;
import com.atb.employeeevaluation.service.EmployeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employes")  // ✅ Attention : "employes" pas "employees"
@RequiredArgsConstructor
public class EmployeController {

    private final EmployeService employeService;

    @PostMapping
    public ResponseEntity<EmployeDTO> create(@Valid @RequestBody EmployeDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeService.createEmploye(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeDTO> update(@PathVariable Long id, @Valid @RequestBody EmployeDTO dto) {
        return ResponseEntity.ok(employeService.updateEmploye(id, dto));
    }

    /**
     * Ce point n'exigeait qu'une authentification : n'importe quel employé
     * parcourait le répertoire en incrémentant l'identifiant. Le DTO ne porte
     * pas de mot de passe, mais l'adresse électronique, le rôle et la hiérarchie
     * suffisent à reconstituer l'organigramme et à cibler un hameçonnage.
     *
     * Chacun voit donc ce que son travail suppose : l'ADMIN tout le monde, un
     * responsable ses subordonnés, un employé lui-même et ses deux supérieurs
     * (que l'interface affiche sur son profil).
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeDTO> getById(@PathVariable Long id, Authentication authentication) {
        EmployeDTO cible = employeService.getEmployeById(id);
        if (!peutVoirEmploye(cible, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(cible);
    }

    private boolean peutVoirEmploye(EmployeDTO cible, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (aRole(authentication, "ADMIN")) {
            return true;
        }
        EmployeDTO moi = employeService.getEmployeByMatricule(authentication.getName());
        if (cible.getId().equals(moi.getId())) {
            return true;
        }
        if (aRole(authentication, "N1")) {
            return moi.getId().equals(cible.getN1Id());
        }
        if (aRole(authentication, "N2")) {
            return moi.getId().equals(cible.getN2Id());
        }
        return cible.getId().equals(moi.getN1Id()) || cible.getId().equals(moi.getN2Id());
    }

    /** Le rôle tel que la chaîne de filtres l'a établi, jamais une donnée du client. */
    private boolean aRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    @GetMapping("/matricule/{matricule}")
    public ResponseEntity<EmployeDTO> getByMatricule(@PathVariable String matricule) {
        return ResponseEntity.ok(employeService.getEmployeByMatricule(matricule));
    }

    @GetMapping
    public ResponseEntity<List<EmployeDTO>> getAll() {
        return ResponseEntity.ok(employeService.getAllEmployes());
    }

    /** Retourne les employés selon leur rôle */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<EmployeDTO>> getByRole(@PathVariable Role role) {
        return ResponseEntity.ok(employeService.getEmployesByRole(role));
    }

    /** Retourne les employés dont n1_id = n1Id (subordonnés directs du N+1) */
    @GetMapping("/sous-n1/{n1Id}")
    public ResponseEntity<List<EmployeDTO>> getSousN1(@PathVariable Long n1Id,
                                                      @RequestParam(name = "type", required = false) TypeAffectation type,
                                                      Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_N1".equals(a.getAuthority()))) {
            EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
            if (!n1Id.equals(current.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(type != null
                ? employeService.getEmployesByN1AndTypeAffectation(n1Id, type)
                : employeService.getEmployesByN1(n1Id));
    }

    /** Retourne les employés dont n2_id = n2Id (subordonnés directs du N+2) */
    @GetMapping("/sous-n2/{n2Id}")
    public ResponseEntity<List<EmployeDTO>> getSousN2(@PathVariable Long n2Id, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_N2".equals(a.getAuthority()))) {
            EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
            if (!n2Id.equals(current.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(employeService.getEmployesByN2(n2Id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeService.deleteEmploye(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/hierarchie")
    public ResponseEntity<Void> assignN1N2(@PathVariable Long id,
                                           @RequestParam(required = false) Long n1Id,
                                           @RequestParam(required = false) Long n2Id) {
        employeService.assignN1N2(id, n1Id, n2Id);
        return ResponseEntity.ok().build();
    }
}