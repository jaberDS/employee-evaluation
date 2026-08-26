package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, Long> {

    Optional<WebAuthnCredential> findByCredentialIdAndActifTrue(String credentialId);

    List<WebAuthnCredential> findByEmployeMatriculeAndActifTrueOrderByCreeLeDesc(String matricule);

    List<WebAuthnCredential> findByUserHandleAndActifTrue(String userHandle);

    boolean existsByEmployeMatriculeAndActifTrue(String matricule);

    Optional<WebAuthnCredential> findByIdAndEmployeMatricule(Long id, String matricule);
}
