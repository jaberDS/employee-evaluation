package com.atb.employeeevaluation.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DashboardStatsDTO {
    private long totalEmployees;
    private long activeEmployees;
    private long totalCampagnes;
    private long openCampagnes;
    private long completedEvaluations;
    private long pendingEvaluations;
    private double averageNote;

    // Prochaine campagne (nearest upcoming, may be null)
    private String prochaineCampagneNom;
    private LocalDateTime prochaineCampagneDateDebut;
    private Integer prochaineCampagneParticipants;
    private Long prochaineCampagneDureeJours;
}
