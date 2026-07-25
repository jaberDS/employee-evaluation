package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.enums.TypeEntite;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "activite_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiviteLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Type d'action effectuée */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeActivite type;

    /** Description lisible de l'activité */
    @Column(nullable = false, length = 255)
    private String description;

    /** Auteur de l'action (peut être null pour actions système/schedulées) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acteur_id")
    private Employe acteur;

    /** Enregistrement concerné par l'action — permet d'afficher son détail. */
    @Column(name = "entite_id")
    private Long entiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entite_type", length = 20)
    private TypeEntite entiteType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
