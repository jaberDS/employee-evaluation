package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private Long id;           // Real employee DB id — needed by N1/N2 dashboards
    private String token;
    private String refreshToken;
    private String matricule;
    private String nom;
    private String prenom;
    private String role;
    private Long expiration;
}
