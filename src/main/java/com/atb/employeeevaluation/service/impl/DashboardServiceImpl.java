package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.DashboardStatsDTO;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.enums.StatutFiche;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import com.atb.employeeevaluation.repository.FicheEvaluationRepository;
import com.atb.employeeevaluation.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final EmployeRepository employeRepository;
    private final EvaluationRepository evaluationRepository;
    private final FicheEvaluationRepository ficheRepository;

    @Override
    public DashboardStatsDTO getStats() {
        DashboardStatsDTO dto = new DashboardStatsDTO();

        // ---- Employés ----
        List<com.atb.employeeevaluation.entity.Employe> employes = employeRepository.findAll();
        dto.setTotalEmployees(employes.size());
        dto.setActiveEmployees(employes.stream()
                .filter(e -> Boolean.TRUE.equals(e.getActif()))
                .count());

        // ---- Campagnes (Evaluations) ----
        List<Evaluation> campagnes = evaluationRepository.findAll();
        dto.setTotalCampagnes(campagnes.size());
        dto.setOpenCampagnes(campagnes.stream()
                .filter(c -> c.getStatut() == StatutCampagne.OUVERTE)
                .count());

        // ---- Fiches d'évaluation ----
        List<FicheEvaluation> fiches = ficheRepository.findAll();
        dto.setCompletedEvaluations(fiches.stream()
                .filter(f -> f.getStatut() == StatutFiche.CLOTUREE)
                .count());
        dto.setPendingEvaluations(fiches.stream()
                .filter(f -> f.getStatut() != StatutFiche.CLOTUREE)
                .count());

        // ---- Note moyenne (fiches clôturées) ----
        double avg = fiches.stream()
                .filter(f -> f.getStatut() == StatutFiche.CLOTUREE && f.getNoteFinale() != null)
                .mapToDouble(FicheEvaluation::getNoteFinale)
                .average()
                .orElse(0.0);
        dto.setAverageNote(Math.round(avg * 10.0) / 10.0);

        // ---- Campagne en cours (statut OUVERTE, la plus récemment démarrée) ----
        campagnes.stream()
                .filter(c -> c.getStatut() == StatutCampagne.OUVERTE)
                .max(Comparator.comparing(Evaluation::getDateDebut))
                .ifPresent(enCours -> {
                    dto.setProchaineCampagneNom(enCours.getNomEvaluation());
                    dto.setProchaineCampagneDateDebut(enCours.getDateDebut());
                    dto.setProchaineCampagneParticipants((int) dto.getTotalEmployees());
                    if (enCours.getDateFin() != null) {
                        dto.setProchaineCampagneDureeJours(
                                Duration.between(enCours.getDateDebut(), enCours.getDateFin()).toDays());
                    }
                });

        return dto;
    }
}
