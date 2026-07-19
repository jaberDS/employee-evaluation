package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.AuthRequest;
import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.dto.RefreshTokenRequest;
import com.atb.employeeevaluation.entity.Employe;

public interface AuthService {
    AuthResponse login(AuthRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String token);
    Employe getCurrentEmploye();
    EmployeDTO getEmployeByMatricule(String matricule);
}