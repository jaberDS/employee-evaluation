package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.AuthRequest;
import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.dto.ChangePasswordRequest;
import com.atb.employeeevaluation.dto.CeremonyOptionsResponse;
import com.atb.employeeevaluation.dto.FaceChallengeResponse;
import com.atb.employeeevaluation.dto.FaceVerifyRequest;
import com.atb.employeeevaluation.dto.LoginResponse;
import com.atb.employeeevaluation.dto.MfaTokenRequest;
import com.atb.employeeevaluation.dto.MfaVerifyRequest;
import com.atb.employeeevaluation.dto.RecoveryStartRequest;
import com.atb.employeeevaluation.dto.RefreshTokenRequest;
import com.atb.employeeevaluation.dto.ResetPasswordRequest;
import com.atb.employeeevaluation.enums.AuthenticatorPreference;
import com.atb.employeeevaluation.service.AuthService;
import com.atb.employeeevaluation.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody AuthRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIp(httpRequest)));
    }

    // ==================== SECOND FACTEUR (connexion) ====================

    @PostMapping("/mfa/webauthn/options")
    public ResponseEntity<CeremonyOptionsResponse> mfaOptions(
            @Valid @RequestBody MfaTokenRequest request,
            @RequestParam(defaultValue = "ANY") AuthenticatorPreference appareil) {
        return ResponseEntity.ok(mfaService.startLoginAssertion(request.getMfaToken(), appareil));
    }

    @PostMapping("/mfa/webauthn/verify")
    public ResponseEntity<LoginResponse> mfaVerify(@Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(mfaService.finishLoginAssertion(request));
    }

    @PostMapping("/mfa/face/challenge")
    public ResponseEntity<FaceChallengeResponse> mfaFaceChallenge(@Valid @RequestBody MfaTokenRequest request) {
        return ResponseEntity.ok(mfaService.startLoginFace(request.getMfaToken()));
    }

    @PostMapping("/mfa/face/verify")
    public ResponseEntity<LoginResponse> mfaFaceVerify(@Valid @RequestBody FaceVerifyRequest request) {
        return ResponseEntity.ok(mfaService.finishLoginFace(request));
    }

    // ==================== RÉCUPÉRATION DE COMPTE ====================

    @PostMapping("/recovery/start")
    public ResponseEntity<LoginResponse> recoveryStart(@Valid @RequestBody RecoveryStartRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(mfaService.startRecovery(request.getMatricule(), clientIp(httpRequest)));
    }

    @PostMapping("/recovery/webauthn/options")
    public ResponseEntity<CeremonyOptionsResponse> recoveryOptions(
            @Valid @RequestBody MfaTokenRequest request,
            @RequestParam(defaultValue = "ANY") AuthenticatorPreference appareil) {
        return ResponseEntity.ok(mfaService.startRecoveryAssertion(request.getMfaToken(), appareil));
    }

    @PostMapping("/recovery/webauthn/verify")
    public ResponseEntity<Map<String, String>> recoveryVerify(@Valid @RequestBody MfaVerifyRequest request) {
        Map<String, String> response = new HashMap<>();
        response.put("resetToken", mfaService.finishRecoveryAssertion(request));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recovery/face/challenge")
    public ResponseEntity<FaceChallengeResponse> recoveryFaceChallenge(
            @Valid @RequestBody MfaTokenRequest request) {
        return ResponseEntity.ok(mfaService.startRecoveryFace(request.getMfaToken()));
    }

    @PostMapping("/recovery/face/verify")
    public ResponseEntity<Map<String, String>> recoveryFaceVerify(
            @Valid @RequestBody FaceVerifyRequest request) {
        Map<String, String> response = new HashMap<>();
        response.put("resetToken", mfaService.finishRecoveryFace(request));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recovery/reset-password")
    public ResponseEntity<Map<String, String>> recoveryReset(@Valid @RequestBody ResetPasswordRequest request) {
        mfaService.resetPassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Mot de passe réinitialisé avec succès");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        authService.logout(token);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Déconnexion réussie");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        String matricule = authentication.getName();
        // Fetch full employee record so the frontend gets the real database ID
        com.atb.employeeevaluation.dto.EmployeDTO employe =
                authService.getEmployeByMatricule(matricule);

        Map<String, Object> response = new HashMap<>();
        response.put("id",        employe.getId());
        response.put("matricule", employe.getMatricule());
        response.put("nom",       employe.getNom());
        response.put("prenom",    employe.getPrenom());
        response.put("email",     employe.getEmail());
        response.put("role",      employe.getRole().toString());
        response.put("actif",     employe.getActif());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Mot de passe modifié avec succès");
        return ResponseEntity.ok(response);
    }

    /** Derrière un reverse proxy, X-Forwarded-For porte l'IP réelle du client. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}