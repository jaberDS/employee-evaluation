package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.enums.TypeAffectation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nomEvaluation;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCampagne statut = StatutCampagne.BROUILLON;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_affectation", nullable = false, length = 10,
            columnDefinition = "VARCHAR(10) DEFAULT 'SIEGE'")
    @Builder.Default
    private TypeAffectation typeAffectation = TypeAffectation.SIEGE;

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FicheEvaluation> fiches = new ArrayList<>();
}
