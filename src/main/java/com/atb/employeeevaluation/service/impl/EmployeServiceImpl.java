package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.EmployeDTO;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.enums.TypeEntite;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.mapper.EmployeMapper;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.service.ActiviteLogService;
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
    private final ActiviteLogService activiteLogService;

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
        if (dto.getN1Id() != null) {
            employe.setN1(findEntityById(dto.getN1Id()));
        }
        if (dto.getN2Id() != null) {
            employe.setN2(findEntityById(dto.getN2Id()));
        }
        employe = employeRepository.save(employe);
        activiteLogService.log(TypeActivite.EMPLOYE_CREE,
                "Nouvel employé ajouté — " + employe.getPrenom() + " " + employe.getNom(),
                TypeEntite.EMPLOYE, employe.getId());
        return employeMapper.toDto(employe);
    }

    @Override
    public EmployeDTO updateEmploye(Long id, EmployeDTO dto) {
        Employe existant = findEntityById(id);
        if (dto.getMatricule() != null && !dto.getMatricule().equals(existant.getMatricule())) {
            if (employeRepository.existsByMatricule(dto.getMatricule())) {
                throw new RuntimeException("Matricule déjà existant");
            }
            existant.setMatricule(dto.getMatricule());
        }
        existant.setNom(dto.getNom());
        existant.setPrenom(dto.getPrenom());
        existant.setEmail(dto.getEmail());
        existant.setRole(dto.getRole());
        existant.setActif(dto.getActif() != null ? dto.getActif() : existant.getActif());
        if (dto.getMotDePasse() != null && !dto.getMotDePasse().isEmpty()) {
            existant.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        }
        if (dto.getN1Id() != null) {
            existant.setN1(findEntityById(dto.getN1Id()));
        } else {
            existant.setN1(null);
        }
        if (dto.getN2Id() != null) {
            existant.setN2(findEntityById(dto.getN2Id()));
        } else {
            existant.setN2(null);
        }
        Employe saved = employeRepository.save(existant);
        activiteLogService.log(TypeActivite.EMPLOYE_MODIFIE,
                "Employé modifié — " + saved.getPrenom() + " " + saved.getNom(),
                TypeEntite.EMPLOYE, saved.getId());
        return employeMapper.toDto(saved);
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
    public List<EmployeDTO> getEmployesByRole(Role role) {
        return employeRepository.findByRole(role).stream()
                .map(employeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EmployeDTO> getEmployesByN1(Long n1Id) {
        return employeRepository.findByN1Id(n1Id).stream()
                .map(employeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteEmploye(Long id) {
        Employe employe = findEntityById(id);
        String nomComplet = employe.getPrenom() + " " + employe.getNom();
        // Nullify N1/N2 references from other employees before deleting
        for (Employe e : employeRepository.findByN1Id(id)) {
            e.setN1(null);
            employeRepository.save(e);
        }
        for (Employe e : employeRepository.findByN2Id(id)) {
            e.setN2(null);
            employeRepository.save(e);
        }
        employeRepository.deleteById(id);
        activiteLogService.log(TypeActivite.EMPLOYE_SUPPRIME,
                "Employé supprimé — " + nomComplet);
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