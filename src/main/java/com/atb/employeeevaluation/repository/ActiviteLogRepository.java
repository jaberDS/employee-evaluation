package com.atb.employeeevaluation.repository;

import com.atb.employeeevaluation.entity.ActiviteLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActiviteLogRepository extends JpaRepository<ActiviteLog, Long> {
    List<ActiviteLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
