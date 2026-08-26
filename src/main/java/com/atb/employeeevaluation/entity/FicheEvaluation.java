package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.Decision;
import com.atb.employeeevaluation.enums.StatutFiche;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fiche_evaluation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "evaluation_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FicheEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @Column(name = "date_creation")
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(columnDefinition = "JSON")
    private String reponsesN1;  // Stocké en JSON: {"questionId": note, ...}

    private Double noteN1;

    @Column(columnDefinition = "TEXT")
    private String commentaireN1;

    @Enumerated(EnumType.STRING)
    private Decision decisionN2;

    @Column(columnDefinition = "TEXT")
    private String commentaireN2;

    @Enumerated(EnumType.STRING)
    private Decision decisionEmploye;

    @Column(columnDefinition = "TEXT")
    private String commentaireEmploye;

    private Double noteFinale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutFiche statut = StatutFiche.EN_ATTENTE;
}