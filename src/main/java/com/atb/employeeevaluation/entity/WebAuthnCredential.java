package com.atb.employeeevaluation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Clé publique d'un authentificateur enrôlé (passkey). La clé privée ne quitte
 * jamais l'enclave sécurisée de l'appareil — on ne stocke ici que de quoi
 * vérifier une signature.
 */
@Entity
@Table(name = "webauthn_credential",
        uniqueConstraints = @UniqueConstraint(name = "uk_wac_credential_id", columnNames = "credential_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    /**
     * Identifiant de la clé, en base64url. Stocké en VARCHAR et non en BLOB :
     * InnoDB ne peut pas poser d'index unique sur un BLOB sans longueur de
     * préfixe, que ddl-auto=update ne sait pas émettre.
     */
    @Column(name = "credential_id", nullable = false, length = 512)
    private String credentialId;

    /** Identifiant opaque de l'utilisateur côté authentificateur, stable par employé. */
    @Column(name = "user_handle", nullable = false, length = 64)
    private String userHandle;

    @Column(name = "public_key_cose", nullable = false, length = 1024)
    private byte[] publicKeyCose;

    /** Compteur anti-rejeu ; doit croître à chaque assertion. */
    @Column(name = "signature_count", nullable = false)
    @Builder.Default
    private Long signatureCount = 0L;

    @Column(length = 36)
    private String aaguid;

    /** Transports annoncés par l'authentificateur, en CSV (usb, nfc, ble, internal, hybrid). */
    @Column(length = 120)
    private String transports;

    /** Nom donné par l'utilisateur (« iPhone de Karim »). */
    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "backup_eligible")
    private Boolean backupEligible;

    @Column(name = "backed_up")
    private Boolean backedUp;

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
