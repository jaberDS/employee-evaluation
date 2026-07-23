package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.ActiviteLogDTO;
import com.atb.employeeevaluation.entity.ActiviteLog;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.mapper.ActiviteLogMapper;
import com.atb.employeeevaluation.repository.ActiviteLogRepository;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.service.ActiviteLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActiviteLogServiceImpl implements ActiviteLogService {

    /** Actions de gestion admin (employés/campagnes/questions) — masquées pour EMPLOYE/N1/N2. */
    private static final Set<TypeActivite> ADMIN_ONLY_TYPES = EnumSet.of(
            TypeActivite.EMPLOYE_CREE, TypeActivite.EMPLOYE_MODIFIE, TypeActivite.EMPLOYE_SUPPRIME,
            TypeActivite.CAMPAGNE_CREEE, TypeActivite.CAMPAGNE_MODIFIEE, TypeActivite.CAMPAGNE_SUPPRIMEE,
            TypeActivite.CAMPAGNE_OUVERTE, TypeActivite.CAMPAGNE_FERMEE, TypeActivite.CAMPAGNE_CLOTUREE,
            TypeActivite.QUESTION_AJOUTEE
    );

    private final ActiviteLogRepository activiteLogRepository;
    private final EmployeRepository employeRepository;
    private final ActiviteLogMapper activiteLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(TypeActivite type, String description) {
        try {
            Employe acteur = getCurrentEmployeOrNull();
            ActiviteLog activite = ActiviteLog.builder()
                    .type(type)
                    .description(description)
                    .acteur(acteur)
                    .build();
            activiteLogRepository.save(activite);
        } catch (Exception e) {
            // Le logging d'activité ne doit jamais casser l'opération métier
            log.warn("Impossible d'enregistrer l'activité {}: {}", type, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiviteLogDTO> getRecentActivities(int limit) {
        if (isAdmin()) {
            return activiteLogRepository
                    .findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                    .stream()
                    .map(activiteLogMapper::toDto)
                    .collect(Collectors.toList());
        }

        // Non-admin: sur-échantillonne puis filtre les actions de gestion admin avant de tronquer.
        return activiteLogRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit * 5))
                .stream()
                .filter(a -> !ADMIN_ONLY_TYPES.contains(a.getType()))
                .limit(limit)
                .map(activiteLogMapper::toDto)
                .collect(Collectors.toList());
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @Override
    @Transactional
    public void deleteAll() {
        activiteLogRepository.deleteAllInBatch();
    }

    private Employe getCurrentEmployeOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return employeRepository.findByMatricule(authentication.getName()).orElse(null);
    }
}
