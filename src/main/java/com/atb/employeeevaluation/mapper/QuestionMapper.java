package com.atb.employeeevaluation.mapper;

import com.atb.employeeevaluation.dto.QuestionDTO;
import com.atb.employeeevaluation.entity.Question;
import org.springframework.stereotype.Component;

@Component
public class QuestionMapper {

    public Question toEntity(QuestionDTO dto) {
        return Question.builder()
                .id(dto.getId())
                .libelle(dto.getLibelle())
                .noteMax(dto.getNoteMax())
                .ordre(dto.getOrdre())
                .build();
    }

    public QuestionDTO toDto(Question entity) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(entity.getId());
        dto.setLibelle(entity.getLibelle());
        dto.setNoteMax(entity.getNoteMax());
        dto.setOrdre(entity.getOrdre());
        dto.setEvaluationId(entity.getEvaluation() != null ? entity.getEvaluation().getId() : null);
        return dto;
    }
}