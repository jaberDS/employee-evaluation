package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.CeremonyType;
import com.atb.employeeevaluation.enums.MfaPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cérémonie WebAuthn en cours. Le serveur doit se souvenir entre /options et
 * /verify du challenge qu'il a émis — sans quoi n'importe quelle signature
 * ferait l'affaire.
 */
@Entity
@Table(name = "webauthn_ceremony",
        uniqueConstraints = @UniqueConstraint(name = "uk_wce_ceremony_id", columnNames = "ceremony_id"),
        indexes = @Index(name = "idx_wce_expire_le", columnList = "expire_le"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCeremony {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ceremony_id", nullable = false, length = 64)
    private String ceremonyId;

    /**
     * Colonne simple, pas de clé étrangère : une cérémonie de récupération peut
     * porter un matricule inconnu (réponse leurre anti-énumération).
     */
    @Column(nullable = false, length = 20)
    private String matricule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CeremonyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MfaPurpose purpose;

    /**
     * L'objet de requête Yubico sérialisé. finishAssertion / finishRegistration
     * ont besoin de la requête d'origine complète, pas seulement du challenge.
     *
     * TEXT explicite et non @Lob : sur MySQL, @Lob sur un String produit un
     * TINYTEXT (255 octets), bien trop court pour ces options (plusieurs Ko).
     */
    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    private String requestJson;

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
