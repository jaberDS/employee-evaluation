package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.AuthRequest;
import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.dto.ChangePasswordRequest;
import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.dto.LoginResponse;
import com.atb.employeeevaluation.dto.RefreshTokenRequest;
import com.atb.employeeevaluation.entity.Employe;

public interface AuthService {
    /**
     * Vérifie le mot de passe. Si un second facteur est enrôlé, renvoie un jeton
     * d'étape au lieu d'une session — les jetons ne sont délivrés qu'après
     * validation du facteur.
     */
    LoginResponse login(AuthRequest request, String clientIp);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String token);
    Employe getCurrentEmploye();
    EmployeDTO getEmployeByMatricule(String matricule);
    void changePassword(ChangePasswordRequest request);
}