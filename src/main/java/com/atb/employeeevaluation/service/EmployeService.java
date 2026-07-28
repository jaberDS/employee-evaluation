package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;

import java.util.List;

public interface EmployeService {
    EmployeDTO createEmploye(EmployeDTO dto);
    EmployeDTO updateEmploye(Long id, EmployeDTO dto);
    EmployeDTO getEmployeById(Long id);
    EmployeDTO getEmployeByMatricule(String matricule);
    List<EmployeDTO> getAllEmployes();
    List<EmployeDTO> getEmployesByRole(Role role);
    List<EmployeDTO> getEmployesByN1(Long n1Id);
    List<EmployeDTO> getEmployesByN2(Long n2Id);
    void deleteEmploye(Long id);
    void assignN1N2(Long employeId, Long n1Id, Long n2Id);
    Employe findEntityById(Long id);
}