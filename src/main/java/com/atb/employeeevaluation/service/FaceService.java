package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.FaceChallengeResponse;
import com.atb.employeeevaluation.dto.FaceStatusDTO;
import com.atb.employeeevaluation.dto.FaceVerifyRequest;
import com.atb.employeeevaluation.enums.MfaPurpose;

public interface FaceService {

    /** Tire une séquence de consignes de vivacité et ouvre une cérémonie. */
    FaceChallengeResponse startCeremony(String matricule, MfaPurpose purpose, boolean decoy);

    /** Inscrit le gabarit facial d'un employé déjà authentifié. */
    FaceStatusDTO enroll(String matricule, FaceVerifyRequest request);

    /**
     * Vérifie une séquence contre le gabarit stocké.
     *
     * @return le matricule prouvé
     */
    String verify(String matricule, FaceVerifyRequest request, MfaPurpose purpose);

    FaceStatusDTO status(String matricule);

    void remove(String matricule, String currentPassword);

    boolean hasTemplate(String matricule);
}
