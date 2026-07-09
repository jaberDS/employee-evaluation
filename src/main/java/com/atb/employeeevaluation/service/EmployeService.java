package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.entity.Employe;

import java.util.List;

public interface EmployeService {
    EmployeDTO createEmploye(EmployeDTO dto);
    EmployeDTO updateEmploye(Long id, EmployeDTO dto);
    EmployeDTO getEmployeById(Long id);
    EmployeDTO getEmployeByMatricule(String matricule);
    List<EmployeDTO> getAllEmployes();
    void deleteEmploye(Long id);
    void assignN1N2(Long employeId, Long n1Id, Long n2Id);
    Employe findEntityById(Long id);
}