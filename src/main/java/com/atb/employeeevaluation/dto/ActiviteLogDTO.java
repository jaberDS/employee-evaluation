package com.atb.employeeevaluation.dto;

import com.atb.employeeevaluation.enums.TypeActivite;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActiviteLogDTO {
    private Long id;
    private TypeActivite type;
    private String description;
    private Long acteurId;
    private String acteurNom;
    private String acteurRole;
    private LocalDateTime createdAt;
}
