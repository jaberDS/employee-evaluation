package com.atb.employeeevaluation.mapper;

import com.atb.employeeevaluation.dto.EvaluationDTO;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.enums.StatutCampagne;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EvaluationMapper {

    private final QuestionMapper questionMapper;

    public Evaluation toEntity(EvaluationDTO dto) {
        return Evaluation.builder()
                .id(dto.getId())
                .nomEvaluation(dto.getNomEvaluation())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .statut(dto.getStatut() != null ? dto.getStatut() : StatutCampagne.BROUILLON)
                .build();
    }

    public EvaluationDTO toDto(Evaluation entity) {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setId(entity.getId());
        dto.setNomEvaluation(entity.getNomEvaluation());
        dto.setDateDebut(entity.getDateDebut());
        dto.setDateFin(entity.getDateFin());
        dto.setStatut(entity.getStatut());
        if (entity.getQuestions() != null && !entity.getQuestions().isEmpty()) {
            dto.setQuestions(entity.getQuestions().stream()
                    .map(questionMapper::toDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}