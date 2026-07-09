package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String matricule;
    private String nom;
    private String prenom;
    private String role;
    private Long expiration;
}