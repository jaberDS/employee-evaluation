package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.FaceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, Long> {

    Optional<FaceTemplate> findByEmployeMatriculeAndActifTrue(String matricule);

    /**
     * Gabarit d'un employé, actif ou non.
     *
     * La contrainte d'unicité porte sur `employe_id` seul, et la suppression se
     * contente de passer `actif` à faux : la ligne survit. Une réinscription
     * doit donc la retrouver pour l'écraser — chercher uniquement parmi les
     * gabarits actifs conduirait à insérer un doublon que la base refuse.
     */
    Optional<FaceTemplate> findByEmployeMatricule(String matricule);

    boolean existsByEmployeMatriculeAndActifTrue(String matricule);
}
