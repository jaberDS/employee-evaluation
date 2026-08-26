package com.atb.employeeevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Question posée à l'assistant. */
@Data
public class AssistantRequest {

    /**
     * La borne haute protège autant le coût que le service : un mur de texte
     * ferait exploser le nombre de jetons sans jamais être une vraie question.
     */
    @NotBlank(message = "La question est obligatoire")
    @Size(max = 1000, message = "Question trop longue (1000 caractères maximum)")
    private String question;
}
