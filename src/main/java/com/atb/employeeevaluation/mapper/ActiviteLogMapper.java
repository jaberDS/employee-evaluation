package com.atb.employeeevaluation.mapper;

import com.atb.employeeevaluation.dto.ActiviteLogDTO;
import com.atb.employeeevaluation.entity.ActiviteLog;
import com.atb.employeeevaluation.entity.Employe;
import org.springframework.stereotype.Component;

@Component
public class ActiviteLogMapper {

    public ActiviteLogDTO toDto(ActiviteLog entity) {
        ActiviteLogDTO dto = new ActiviteLogDTO();
        dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setDescription(entity.getDescription());
        dto.setCreatedAt(entity.getCreatedAt());
        Employe acteur = entity.getActeur();
        if (acteur != null) {
            dto.setActeurId(acteur.getId());
            dto.setActeurNom(acteur.getPrenom() + " " + acteur.getNom());
            dto.setActeurRole(acteur.getRole().name());
        }
        return dto;
    }
}
