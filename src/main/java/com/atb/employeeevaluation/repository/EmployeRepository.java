package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.enums.TypeAffectation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeRepository extends JpaRepository<Employe, Long> {
    Optional<Employe> findByMatricule(String matricule);
    Optional<Employe> findByEmail(String email);
    boolean existsByMatricule(String matricule);
    boolean existsByEmail(String email);
    List<Employe> findByRole(Role role);
    List<Employe> findByN1Id(Long n1Id);
    List<Employe> findByN1IdAndTypeAffectation(Long n1Id, TypeAffectation typeAffectation);
    List<Employe> findByN2Id(Long n2Id);
}