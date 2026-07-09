package com.atb.employeeevaluation.mapper;

import com.atb.employeeevaluation.dto.FicheEvaluationDTO;
import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FicheEvaluationMapper {

    private final ObjectMapper objectMapper;

    public FicheEvaluationDTO toDto(FicheEvaluation entity) {
        FicheEvaluationDTO dto = new FicheEvaluationDTO();
        dto.setId(entity.getId());
        dto.setEmployeId(entity.getEmploye().getId());
        dto.setEmployeNom(entity.getEmploye().getNom());
        dto.setEmployePrenom(entity.getEmploye().getPrenom());
        dto.setEvaluationId(entity.getEvaluation().getId());
        dto.setEvaluationNom(entity.getEvaluation().getNomEvaluation());
        dto.setDateCreation(entity.getDateCreation());
        dto.setNoteN1(entity.getNoteN1());
        dto.setCommentaireN1(entity.getCommentaireN1());
        dto.setDecisionN2(entity.getDecisionN2());
        dto.setCommentaireN2(entity.getCommentaireN2());
        dto.setDecisionEmploye(entity.getDecisionEmploye());
        dto.setNoteFinale(entity.getNoteFinale());
        dto.setStatut(entity.getStatut());

        // Convertir le JSON en Map
        if (entity.getReponsesN1() != null) {
            try {
                Map<Long, Integer> reponses = objectMapper.readValue(
                        entity.getReponsesN1(),
                        new TypeReference<Map<Long, Integer>>() {}
                );
                dto.setReponsesN1(reponses);
            } catch (Exception e) {
                dto.setReponsesN1(new HashMap<>());
            }
        }

        return dto;
    }
}