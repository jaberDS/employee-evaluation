package com.atb.employeeevaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Trames capturées, rattachées à la cérémonie qui a émis les consignes. */
@Data
public class FaceVerifyRequest {

    /** Absent lors de l'inscription depuis le profil (la session suffit). */
    private String mfaToken;

    @NotBlank(message = "La cérémonie est obligatoire")
    private String ceremonyId;

    @Valid
    @NotEmpty(message = "Aucune image capturée")
    @Size(max = 48, message = "Trop d'images envoyées")
    private List<FaceFrameDTO> frames;
}
