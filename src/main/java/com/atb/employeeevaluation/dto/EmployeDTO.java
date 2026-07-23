package com.atb.employeeevaluation.dto;

import com.atb.employeeevaluation.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmployeDTO {
    private Long id;
    @NotBlank private String matricule;
    @NotBlank private String nom;
    @NotBlank private String prenom;
    @Email @NotBlank private String email;
    private String motDePasse;
    @NotNull private Role role;
    private Long n1Id;
    private String n1Nom;
    private String n1Prenom;
    private Long n2Id;
    private String n2Nom;
    private String n2Prenom;
    private Boolean actif;
}