package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.FaceChallengeResponse;
import com.atb.employeeevaluation.dto.FaceStatusDTO;
import com.atb.employeeevaluation.dto.FaceVerifyRequest;
import com.atb.employeeevaluation.enums.MfaPurpose;
import com.atb.employeeevaluation.service.FaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Inscription faciale depuis le profil.
 *
 * Sous /api/mfa/** : une session valide est exigée, sinon n'importe qui pourrait
 * enrôler son propre visage sur le compte d'un autre.
 */
@RestController
@RequestMapping("/api/mfa/face")
@RequiredArgsConstructor
public class FaceMfaController {

    private final FaceService faceService;

    @GetMapping
    public ResponseEntity<FaceStatusDTO> status(Authentication authentication) {
        return ResponseEntity.ok(faceService.status(authentication.getName()));
    }

    @PostMapping("/challenge")
    public ResponseEntity<FaceChallengeResponse> challenge(Authentication authentication) {
        return ResponseEntity.ok(faceService.startCeremony(
                authentication.getName(), MfaPurpose.ENROLLMENT, false));
    }

    @PostMapping("/enroll")
    public ResponseEntity<FaceStatusDTO> enroll(@Valid @RequestBody FaceVerifyRequest request,
                                                Authentication authentication) {
        return ResponseEntity.ok(faceService.enroll(authentication.getName(), request));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> remove(@Valid @RequestBody DeleteRequest request,
                                                      Authentication authentication) {
        faceService.remove(authentication.getName(), request.getCurrentPassword());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Reconnaissance faciale désactivée");
        return ResponseEntity.ok(response);
    }

    @Data
    public static class DeleteRequest {
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        private String currentPassword;
    }
}
