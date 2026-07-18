package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.mapper.EmployeMapper;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.service.EmployeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeServiceImpl implements EmployeService {

    private final EmployeRepository employeRepository;
    private final EmployeMapper employeMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public EmployeDTO createEmploye(EmployeDTO dto) {
        if (dto.getMotDePasse() == null || dto.getMotDePasse().isBlank()) {
            throw new RuntimeException("Le mot de passe est obligatoire");
        }
        if (employeRepository.existsByMatricule(dto.getMatricule())) {
            throw new RuntimeException("Matricule déjà existant");
        }
        if (employeRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email déjà existant");
        }
        Employe employe = employeMapper.toEntity(dto);
        employe.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        employe = employeRepository.save(employe);
        return employeMapper.toDto(employe);
    }

    @Override
    public EmployeDTO updateEmploye(Long id, EmployeDTO dto) {
        Employe existant = findEntityById(id);
        existant.setNom(dto.getNom());
        existant.setPrenom(dto.getPrenom());
        existant.setEmail(dto.getEmail());
        existant.setRole(dto.getRole());
        existant.setActif(dto.getActif() != null ? dto.getActif() : existant.getActif());
        if (dto.getMotDePasse() != null && !dto.getMotDePasse().isEmpty()) {
            existant.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        }
        return employeMapper.toDto(employeRepository.save(existant));
    }

    @Override
    public EmployeDTO getEmployeById(Long id) {
        return employeMapper.toDto(findEntityById(id));
    }

    @Override
    public EmployeDTO getEmployeByMatricule(String matricule) {
        Employe employe = employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Employé non trouvé avec matricule: " + matricule));
        return employeMapper.toDto(employe);
    }

    @Override
    public List<EmployeDTO> getAllEmployes() {
        return employeRepository.findAll().stream()
                .map(employeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteEmploye(Long id) {
        if (!employeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employé non trouvé avec id: " + id);
        }
        employeRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void assignN1N2(Long employeId, Long n1Id, Long n2Id) {
        Employe employe = findEntityById(employeId);
        if (n1Id != null) {
            Employe n1 = findEntityById(n1Id);
            employe.setN1(n1);
        } else {
            employe.setN1(null);
        }
        if (n2Id != null) {
            Employe n2 = findEntityById(n2Id);
            employe.setN2(n2);
        } else {
            employe.setN2(null);
        }
        employeRepository.save(employe);
    }

    @Override
    public Employe findEntityById(Long id) {
        return employeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé non trouvé avec id: " + id));
    }
}