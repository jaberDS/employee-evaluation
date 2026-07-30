package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** État de l'inscription faciale, affiché dans la section Sécurité du profil. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceStatusDTO {

    private boolean enrolled;
    private Double qualite;
    private LocalDateTime creeLe;
}
