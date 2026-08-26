package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {
    @NotBlank(message = "Le token est obligatoire")
    private String token;
}