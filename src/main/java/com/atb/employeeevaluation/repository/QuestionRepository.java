package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByEvaluationIdOrderByOrdreAsc(Long evaluationId);

    void deleteByEvaluationId(Long evaluationId);

    boolean existsByEvaluationId(Long evaluationId);

    long countByEvaluationId(Long evaluationId);

    /** Vérifie qu'un ordre est déjà utilisé dans une évaluation */
    boolean existsByEvaluationIdAndOrdre(Long evaluationId, Integer ordre);

    /** Vérifie unicité de l'ordre en excluant la question en cours de modification */
    boolean existsByEvaluationIdAndOrdreAndIdNot(Long evaluationId, Integer ordre, Long id);
}
