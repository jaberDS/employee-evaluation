package com.atb.employeeevaluation.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String matricule;
    private String motDePasse;
}