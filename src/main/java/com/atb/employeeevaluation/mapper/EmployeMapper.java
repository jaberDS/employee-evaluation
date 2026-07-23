package com.atb.employeeevaluation.mapper;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.entity.Employe;
import org.springframework.stereotype.Component;

@Component
public class EmployeMapper {

    public Employe toEntity(EmployeDTO dto) {
        return Employe.builder()
                .id(dto.getId())
                .matricule(dto.getMatricule())
                .nom(dto.getNom())
                .prenom(dto.getPrenom())
                .email(dto.getEmail())
                .motDePasse(dto.getMotDePasse())
                .role(dto.getRole())
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .build();
    }

    public EmployeDTO toDto(Employe entity) {
        EmployeDTO dto = new EmployeDTO();
        dto.setId(entity.getId());
        dto.setMatricule(entity.getMatricule());
        dto.setNom(entity.getNom());
        dto.setPrenom(entity.getPrenom());
        dto.setEmail(entity.getEmail());
        dto.setMotDePasse(null);
        dto.setRole(entity.getRole());
        dto.setN1Id(entity.getN1() != null ? entity.getN1().getId() : null);
        dto.setN1Nom(entity.getN1() != null ? entity.getN1().getNom() : null);
        dto.setN1Prenom(entity.getN1() != null ? entity.getN1().getPrenom() : null);
        dto.setN2Id(entity.getN2() != null ? entity.getN2().getId() : null);
        dto.setN2Nom(entity.getN2() != null ? entity.getN2().getNom() : null);
        dto.setN2Prenom(entity.getN2() != null ? entity.getN2().getPrenom() : null);
        dto.setActif(entity.getActif());
        return dto;
    }
}