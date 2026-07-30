package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Vue d'un passkey enrôlé, telle qu'affichée sur la page profil. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialSummaryDTO {

    private Long id;
    private String label;
    private String transports;
    private LocalDateTime creeLe;
    private LocalDateTime derniereUtilisation;

    /** Vrai si la clé est synchronisée (trousseau iCloud, gestionnaire Google). */
    private Boolean backedUp;
}
