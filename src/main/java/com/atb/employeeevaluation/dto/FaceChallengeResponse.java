package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Séquence de consignes tirée par le serveur pour une cérémonie faciale. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceChallengeResponse {

    private String ceremonyId;
    private List<Step> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private String action;
        private String instruction;
        private int dureeMs;
    }
}
