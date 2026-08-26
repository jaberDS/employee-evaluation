package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse de POST /api/auth/login.
 *
 * Sans facteur enrôlé, {@code mfaRequired} est faux et {@code session} porte la
 * réponse habituelle : le parcours existant est inchangé. Sinon, le client doit
 * présenter un second facteur avant d'obtenir des jetons.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private boolean mfaRequired;

    /** Jeton court « mot de passe vérifié ». Circule dans le corps, jamais en en-tête. */
    private String mfaToken;

    /** Facteurs disponibles pour ce compte : WEBAUTHN, et plus tard FACE. */
    private List<String> factors;

    private AuthResponse session;
}
