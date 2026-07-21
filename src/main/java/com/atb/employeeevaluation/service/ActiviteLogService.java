package com.atb.employeeevaluation.service;

import com.atb.employeeevaluation.dto.ActiviteLogDTO;
import com.atb.employeeevaluation.enums.TypeActivite;

import java.util.List;

public interface ActiviteLogService {
    /** Enregistre une activité effectuée par l'utilisateur courant (si authentifié). */
    void log(TypeActivite type, String description);

    /** Retourne les N dernières activités, les plus récentes en premier. */
    List<ActiviteLogDTO> getRecentActivities(int limit);

    /** Supprime tout l'historique des activités. */
    void deleteAll();
}
