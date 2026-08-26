package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.MfaPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cérémonie faciale en cours.
 *
 * Le serveur doit se souvenir de la séquence de consignes qu'il a tirée : sans
 * cela, un client pourrait annoncer lui-même les actions qu'il a exécutées et
 * la vivacité active ne prouverait plus rien.
 */
@Entity
@Table(name = "face_ceremony",
        uniqueConstraints = @UniqueConstraint(name = "uk_fce_ceremony_id", columnNames = "ceremony_id"),
        indexes = @Index(name = "idx_fce_expire_le", columnList = "expire_le"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceCeremony {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ceremony_id", nullable = false, length = 64)
    private String ceremonyId;

    /** Colonne simple, sans clé étrangère : une récupération peut porter un matricule inconnu. */
    @Column(nullable = false, length = 20)
    private String matricule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MfaPurpose purpose;

    /** Noms de LivenessAction séparés par des virgules, dans l'ordre exigé. */
    @Column(nullable = false, length = 200)
    private String actions;

    /** Cérémonie leurre : elle se déroule normalement mais échoue toujours. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean decoy = false;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "expire_le", nullable = false)
    private LocalDateTime expireLe;

    @Column(nullable = false)
    @Builder.Default
    private Boolean consomme = false;

    @PrePersist
    void prePersist() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }
}
