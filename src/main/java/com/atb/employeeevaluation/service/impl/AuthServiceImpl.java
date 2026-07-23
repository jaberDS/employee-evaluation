package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.AuthRequest;
import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.dto.ChangePasswordRequest;
import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.dto.RefreshTokenRequest;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.exception.UnauthorizedOperationException;
import com.atb.employeeevaluation.mapper.EmployeMapper;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.security.JwtUtil;
import com.atb.employeeevaluation.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmployeRepository employeRepository;
    private final EmployeMapper employeMapper;
    private final PasswordEncoder passwordEncoder;

    // Blacklist des tokens (en mémoire - pour démo)
    // En production, utiliser Redis ou une base de données
    private final Set<String> tokenBlacklist = new HashSet<>();

    @Override
    public AuthResponse login(AuthRequest request) {
        log.info("Tentative de connexion pour: {}", request.getMatricule());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getMatricule(), request.getMotDePasse())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Employe employe = employeRepository.findByMatricule(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Employé non trouvé"));

        String token = jwtUtil.generateToken(userDetails.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

        log.info("Connexion réussie pour: {}", employe.getMatricule());

        return new AuthResponse(
                employe.getId(),
                token,
                refreshToken,
                employe.getMatricule(),
                employe.getNom(),
                employe.getPrenom(),
                employe.getRole().name(),
                jwtUtil.extractExpiration(token).getTime()
        );
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (tokenBlacklist.contains(refreshToken)) {
            throw new UnauthorizedOperationException("Refresh token invalide ou révoqué");
        }

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedOperationException("Refresh token invalide");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        Employe employe = employeRepository.findByMatricule(username)
                .orElseThrow(() -> new RuntimeException("Employé non trouvé"));

        String newToken = jwtUtil.generateToken(username);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);

        tokenBlacklist.add(refreshToken);

        return new AuthResponse(
                employe.getId(),
                newToken,
                newRefreshToken,
                employe.getMatricule(),
                employe.getNom(),
                employe.getPrenom(),
                employe.getRole().name(),
                jwtUtil.extractExpiration(newToken).getTime()
        );
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenBlacklist.add(token);
            log.info("Token ajouté à la blacklist");
        }
        SecurityContextHolder.clearContext();
        log.info("Déconnexion effectuée");
    }

    @Override
    public Employe getCurrentEmploye() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedOperationException("Utilisateur non authentifié");
        }
        String matricule = authentication.getName();
        return employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new RuntimeException("Employé non trouvé"));
    }

    @Override
    public EmployeDTO getEmployeByMatricule(String matricule) {
        Employe employe = employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Employé non trouvé: " + matricule));
        return employeMapper.toDto(employe);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Employe employe = getCurrentEmploye();

        if (!passwordEncoder.matches(request.getCurrentPassword(), employe.getMotDePasse())) {
            throw new UnauthorizedOperationException("Le mot de passe actuel est incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), employe.getMotDePasse())) {
            throw new UnauthorizedOperationException("Le nouveau mot de passe doit différer de l'ancien");
        }

        employe.setMotDePasse(passwordEncoder.encode(request.getNewPassword()));
        employeRepository.save(employe);
        log.info("Mot de passe changé pour: {}", employe.getMatricule());
    }
}