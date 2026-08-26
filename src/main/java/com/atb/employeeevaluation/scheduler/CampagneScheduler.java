package com.atb.employeeevaluation.scheduler;

import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampagneScheduler {

    private final EvaluationRepository evaluationRepository;

    /**
     * Every minute: open BROUILLON campaigns whose dateDebut has been reached
     * and that have at least one question.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void ouvrirCampagnesEligibles() {
        LocalDateTime now = LocalDateTime.now();

        List<Evaluation> candidates = evaluationRepository
                .findByDateDebutBeforeAndStatut(now, StatutCampagne.BROUILLON);

        for (Evaluation ev : candidates) {
            if (!ev.getQuestions().isEmpty()) {
                ev.setStatut(StatutCampagne.OUVERTE);
                evaluationRepository.save(ev);
                log.info("Campagne '{}' (id={}) ouverte automatiquement à {}",
                        ev.getNomEvaluation(), ev.getId(), now);
            } else {
                log.debug("Campagne '{}' (id={}) ignorée : aucune question.",
                        ev.getNomEvaluation(), ev.getId());
            }
        }
    }

    /**
     * Every minute: close OUVERTE campaigns whose dateFin has passed.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void fermerCampagnesExpirees() {
        LocalDateTime now = LocalDateTime.now();

        List<Evaluation> expired = evaluationRepository
                .findByDateFinBeforeAndStatut(now, StatutCampagne.OUVERTE);

        for (Evaluation ev : expired) {
            ev.setStatut(StatutCampagne.FERMEE);
            evaluationRepository.save(ev);
            log.info("Campagne '{}' (id={}) fermée automatiquement à {}",
                    ev.getNomEvaluation(), ev.getId(), now);
        }
    }
}
