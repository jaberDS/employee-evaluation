package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.ActiviteDetailDTO;
import com.atb.employeeevaluation.dto.ActiviteLogDTO;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.enums.TypeEntite;

import java.util.List;

public interface ActiviteLogService {
    /** Enregistre une activité effectuée par l'utilisateur courant (si authentifié). */
    void log(TypeActivite type, String description);

    /** Enregistre une activité en la reliant à l'enregistrement concerné. */
    void log(TypeActivite type, String description, TypeEntite entiteType, Long entiteId);

    /** Retourne les N dernières activités, les plus récentes en premier. */
    List<ActiviteLogDTO> getRecentActivities(int limit);

    /** Retourne le détail complet (déroulé du workflow) de l'enregistrement lié à une activité. */
    ActiviteDetailDTO getActiviteDetail(Long activiteId);

    /** Supprime tout l'historique des activités. */
    void deleteAll();
}
