package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.WebAuthnCeremony;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WebAuthnCeremonyRepository extends JpaRepository<WebAuthnCeremony, Long> {

    Optional<WebAuthnCeremony> findByCeremonyIdAndConsommeFalse(String ceremonyId);

    @Modifying
    @Query("DELETE FROM WebAuthnCeremony c WHERE c.expireLe < :seuil")
    int deleteExpirees(@Param("seuil") LocalDateTime seuil);
}
