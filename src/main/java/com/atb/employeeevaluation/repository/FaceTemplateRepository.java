package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.FaceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, Long> {

    Optional<FaceTemplate> findByEmployeMatriculeAndActifTrue(String matricule);

    boolean existsByEmployeMatriculeAndActifTrue(String matricule);
}
