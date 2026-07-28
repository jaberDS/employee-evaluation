package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.atb.employeeevaluation.enums.StatutFiche;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FicheEvaluationRepository extends JpaRepository<FicheEvaluation, Long> {
    Optional<FicheEvaluation> findByEmployeIdAndEvaluationId(Long employeId, Long evaluationId);

    List<FicheEvaluation> findByEmployeId(Long employeId);
    List<FicheEvaluation> findByEvaluationId(Long evaluationId);
    List<FicheEvaluation> findByStatut(StatutFiche statut);
    List<FicheEvaluation> findByEmployeIdAndStatut(Long employeId, StatutFiche statut);
    List<FicheEvaluation> findByEvaluationIdAndStatut(Long evaluationId, StatutFiche statut);

    boolean existsByEmployeIdAndEvaluationId(Long employeId, Long evaluationId);

    /** Toutes les fiches des employés rattachés à un N+1 donné */
    List<FicheEvaluation> findByEmployeN1Id(Long n1Id);

    /** Toutes les fiches des employés rattachés à un N+2 donné */
    List<FicheEvaluation> findByEmployeN2Id(Long n2Id);

    /** Fiches d'un statut donné, limitées aux employés rattachés à un N+2 */
    List<FicheEvaluation> findByEmployeN2IdAndStatut(Long n2Id, StatutFiche statut);
}