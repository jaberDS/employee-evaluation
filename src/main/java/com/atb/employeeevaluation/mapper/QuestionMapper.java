package com.atb.employeeevaluation.mapper;

import com.atb.employeeevaluation.dto.QuestionDTO;
import com.atb.employeeevaluation.entity.Question;
import com.atb.employeeevaluation.enums.TypeQuestion;
import org.springframework.stereotype.Component;

@Component
public class QuestionMapper {

    public Question toEntity(QuestionDTO dto) {
        return Question.builder()
                .id(dto.getId())
                .libelle(dto.getLibelle())
                .description(dto.getDescription())
                .noteMax(dto.getNoteMax() != null ? dto.getNoteMax() : 10)
                .ordre(dto.getOrdre())
                .typeQuestion(dto.getTypeQuestion() != null ? dto.getTypeQuestion() : TypeQuestion.NOTE)
                .obligatoire(dto.getObligatoire() != null ? dto.getObligatoire() : true)
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .build();
    }

    public QuestionDTO toDto(Question entity) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(entity.getId());
        dto.setLibelle(entity.getLibelle());
        dto.setDescription(entity.getDescription());
        dto.setNoteMax(entity.getNoteMax() != null ? entity.getNoteMax() : 10);
        dto.setOrdre(entity.getOrdre());
        dto.setTypeQuestion(entity.getTypeQuestion() != null ? entity.getTypeQuestion() : TypeQuestion.NOTE);
        dto.setObligatoire(entity.getObligatoire() != null ? entity.getObligatoire() : true);
        dto.setActif(entity.getActif() != null ? entity.getActif() : true);
        dto.setEvaluationId(entity.getEvaluation() != null ? entity.getEvaluation().getId() : null);
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
