package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.CeremonyOptionsResponse;
import com.atb.employeeevaluation.dto.CredentialSummaryDTO;
import com.atb.employeeevaluation.dto.RegisterCredentialRequest;
import com.atb.employeeevaluation.enums.AuthenticatorPreference;
import com.atb.employeeevaluation.service.WebAuthnService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion des clés d'accès depuis le profil.
 *
 * Sous /api/mfa/** et non /api/auth/** : ces endpoints exigent une session
 * valide, sinon n'importe qui pourrait enrôler une clé sur un autre compte.
 */
@RestController
@RequestMapping("/api/mfa/webauthn")
@RequiredArgsConstructor
public class MfaController {

    private final WebAuthnService webAuthnService;

    @PostMapping("/register/options")
    public ResponseEntity<CeremonyOptionsResponse> registerOptions(
            @RequestParam(defaultValue = "ANY") AuthenticatorPreference appareil,
            Authentication authentication) {
        return ResponseEntity.ok(webAuthnService.startRegistration(authentication.getName(), appareil));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<Map<String, String>> registerVerify(
            @Valid @RequestBody RegisterCredentialRequest request,
            Authentication authentication) {

        webAuthnService.finishRegistration(authentication.getName(), request.getCeremonyId(),
                request.getLabel(), request.getCredential());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Clé d'accès enregistrée avec succès");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credentials")
    public ResponseEntity<List<CredentialSummaryDTO>> credentials(Authentication authentication) {
        return ResponseEntity.ok(webAuthnService.listCredentials(authentication.getName()));
    }

    @PatchMapping("/credentials/{id}")
    public ResponseEntity<Map<String, String>> rename(@PathVariable Long id,
                                                      @Valid @RequestBody RenameRequest request,
                                                      Authentication authentication) {
        webAuthnService.renameCredential(authentication.getName(), id, request.getLabel());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Clé d'accès renommée");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id,
                                                      @Valid @RequestBody DeleteRequest request,
                                                      Authentication authentication) {
        webAuthnService.deleteCredential(authentication.getName(), id, request.getCurrentPassword());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Clé d'accès supprimée");
        return ResponseEntity.ok(response);
    }

    @Data
    public static class RenameRequest {
        @NotBlank(message = "Le nom de l'appareil est obligatoire")
        @Size(max = 60, message = "Le nom de l'appareil ne peut pas dépasser 60 caractères")
        private String label;
    }

    @Data
    public static class DeleteRequest {
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        private String currentPassword;
    }
}
