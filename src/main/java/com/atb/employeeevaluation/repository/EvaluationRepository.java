package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.enums.StatutCampagne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByStatut(StatutCampagne statut);
    List<Evaluation> findByDateDebutBeforeAndStatut(LocalDateTime date, StatutCampagne statut);
    List<Evaluation> findByDateFinBeforeAndStatut(LocalDateTime date, StatutCampagne statut);
}