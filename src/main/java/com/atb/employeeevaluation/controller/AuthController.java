package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.AuthRequest;
import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.dto.RefreshTokenRequest;
import com.atb.employeeevaluation.service.AuthService;
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

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
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
}