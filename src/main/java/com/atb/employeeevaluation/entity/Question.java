package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.TypeQuestion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Intitulé principal de la question */
    @Column(nullable = false, length = 500)
    private String libelle;

    /** Description / consigne optionnelle */
    @Column(length = 1000)
    private String description;

    /** Note maximale (pour le type NOTE) */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 10")
    @Builder.Default
    private Integer noteMax = 10;

    /** Ordre d'affichage dans la campagne (unique par évaluation) */
    @Column(nullable = false)
    private Integer ordre;

    /** Type de réponse attendu */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'NOTE'")
    @Builder.Default
    private TypeQuestion typeQuestion = TypeQuestion.NOTE;

    /** La réponse est-elle obligatoire ? */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    @Builder.Default
    private Boolean obligatoire = true;

    /** La question est-elle active ? */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    @Builder.Default
    private Boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;
}
