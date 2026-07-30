package com.atb.employeeevaluation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Gabarit facial d'un employé.
 *
 * On ne stocke jamais l'image, seulement le vecteur ArcFace à 512 dimensions.
 * Un embedding n'est pas réversible en photo, ce qui limite fortement l'impact
 * d'une fuite de la table — contrairement à une banque de selfies.
 */
@Entity
@Table(name = "face_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_ft_employe", columnNames = "employe_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    /**
     * 512 floats little-endian = 2048 octets. Pas de @Lob : sur MySQL cela
     * donnerait un TINYBLOB de 255 octets, bien trop court.
     */
    @Column(name = "embedding", nullable = false, length = 2048)
    private byte[] embedding;

    /** Nombre de dimensions réellement stockées, pour survivre à un changement de modèle. */
    @Column(nullable = false)
    private Integer dimensions;

    /** Score de qualité de l'inscription, dans [0, 1]. */
    @Column(nullable = false)
    private Double qualite;

    @Column(name = "modele", length = 40)
    private String modele;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "derniere_utilisation")
    private LocalDateTime derniereUtilisation;

    @PrePersist
    void prePersist() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }
}
