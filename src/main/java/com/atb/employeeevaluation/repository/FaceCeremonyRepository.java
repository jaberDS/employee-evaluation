package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.FaceCeremony;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FaceCeremonyRepository extends JpaRepository<FaceCeremony, Long> {

    Optional<FaceCeremony> findByCeremonyIdAndConsommeFalse(String ceremonyId);

    @Modifying
    @Query("DELETE FROM FaceCeremony c WHERE c.expireLe < :seuil")
    int deleteExpirees(@Param("seuil") LocalDateTime seuil);
}
