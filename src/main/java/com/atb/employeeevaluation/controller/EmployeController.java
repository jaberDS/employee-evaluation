package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.enums.Role;
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

    @GetMapping("/{id}")
    public ResponseEntity<EmployeDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.getEmployeById(id));
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
    public ResponseEntity<List<EmployeDTO>> getSousN1(@PathVariable Long n1Id, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_N1".equals(a.getAuthority()))) {
            EmployeDTO current = employeService.getEmployeByMatricule(authentication.getName());
            if (!n1Id.equals(current.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(employeService.getEmployesByN1(n1Id));
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