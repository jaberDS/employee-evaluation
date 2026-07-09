package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.AuthRequest;
import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmployeRepository employeRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getMatricule(), request.getMotDePasse())
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails.getUsername());
        Employe employe = employeRepository.findByMatricule(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Employé non trouvé"));

        return ResponseEntity.ok(new AuthResponse(
                token,
                jwtUtil.generateRefreshToken(userDetails.getUsername()),
                employe.getMatricule(),
                employe.getNom(),
                employe.getPrenom(),
                employe.getRole().name(),
                jwtUtil.extractExpiration(token).getTime()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("username", authentication.getName());
        response.put("authorities", authentication.getAuthorities());
        response.put("authenticated", authentication.isAuthenticated());
        return ResponseEntity.ok(response);
    }
}