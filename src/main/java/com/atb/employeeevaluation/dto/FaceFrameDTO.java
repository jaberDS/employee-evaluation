package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Une trame de capture faciale.
 *
 * L'image passe en base64 dans le corps JSON plutôt qu'en multipart : la limite
 * multipart par défaut de Boot est de 1 Mo et n'est pas configurée ici. La borne
 * @Size ci-dessous tient donc ce rôle de garde-fou.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaceFrameDTO {

    @NotBlank(message = "L'action est obligatoire")
    @Size(max = 20)
    private String action;

    /** ~4 Mo de base64, soit environ 3 Mo d'image décodée. Large pour du 1080p. */
    @NotBlank(message = "L'image est obligatoire")
    @Size(max = 4_000_000, message = "Image trop volumineuse")
    private String image;
}
