package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.ActiviteDetailDTO;
import com.atb.employeeevaluation.dto.ActiviteLogDTO;
import com.atb.employeeevaluation.entity.ActiviteLog;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.atb.employeeevaluation.enums.StatutFiche;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.enums.TypeEntite;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.exception.UnauthorizedOperationException;
import com.atb.employeeevaluation.mapper.ActiviteLogMapper;
import com.atb.employeeevaluation.repository.ActiviteLogRepository;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import com.atb.employeeevaluation.repository.FicheEvaluationRepository;
import com.atb.employeeevaluation.service.ActiviteLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
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
            TypeActivite.QUESTION_AJOUTEE,
            // Les échecs d'authentification forte n'intéressent que l'administration
            TypeActivite.MFA_ECHOUEE
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ActiviteLogRepository activiteLogRepository;
    private final EmployeRepository employeRepository;
    private final FicheEvaluationRepository ficheRepository;
    private final EvaluationRepository evaluationRepository;
    private final ActiviteLogMapper activiteLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(TypeActivite type, String description) {
        log(type, description, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(TypeActivite type, String description, TypeEntite entiteType, Long entiteId) {
        try {
            Employe acteur = getCurrentEmployeOrNull();
            ActiviteLog activite = ActiviteLog.builder()
                    .type(type)
                    .description(description)
                    .acteur(acteur)
                    .entiteType(entiteType)
                    .entiteId(entiteId)
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

    @Override
    @Transactional(readOnly = true)
    public ActiviteDetailDTO getActiviteDetail(Long activiteId) {
        ActiviteLog activite = activiteLogRepository.findById(activiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée avec id: " + activiteId));

        // Même règle de visibilité que la liste : un non-admin ne peut pas lire une activité admin.
        if (!isAdmin() && ADMIN_ONLY_TYPES.contains(activite.getType())) {
            throw new UnauthorizedOperationException("Accès refusé au détail de cette activité");
        }

        ActiviteDetailDTO dto = new ActiviteDetailDTO();

        if (activite.getEntiteType() == null || activite.getEntiteId() == null) {
            dto.setTitre(activite.getDescription());
            dto.setEntiteSupprimee(true);
            return dto;
        }

        switch (activite.getEntiteType()) {
            case FICHE -> buildFicheDetail(dto, activite);
            case EMPLOYE -> buildEmployeDetail(dto, activite);
            case CAMPAGNE -> buildCampagneDetail(dto, activite);
        }
        return dto;
    }

    // ===================== Construction des détails =====================

    private void buildFicheDetail(ActiviteDetailDTO dto, ActiviteLog activite) {
        FicheEvaluation fiche = ficheRepository.findById(activite.getEntiteId()).orElse(null);
        if (fiche == null) {
            markSupprimee(dto, activite, "Cette évaluation a été supprimée");
            return;
        }

        Employe employe = fiche.getEmploye();
        dto.setTitre("Évaluation de " + fullName(employe));
        dto.setSousTitre(fiche.getEvaluation().getNomEvaluation());
        dto.setStatut(fiche.getStatut().name());

        addInfo(dto, "Employé", fullName(employe));
        addInfo(dto, "Campagne", fiche.getEvaluation().getNomEvaluation());
        addInfo(dto, "Responsable N+1", fullName(employe.getN1()));
        addInfo(dto, "Responsable N+2", fullName(employe.getN2()));
        addInfo(dto, "Note N+1", fiche.getNoteN1() != null ? fiche.getNoteN1() + " / 10" : null);
        addInfo(dto, "Note finale", fiche.getNoteFinale() != null ? fiche.getNoteFinale() + " / 10" : null);

        // Le déroulé provient du journal d'activités : acteurs et dates réels.
        for (ActiviteLog etapeLog : historique(activite)) {
            ActiviteDetailDTO.EtapeDTO etape = baseEtape(etapeLog);
            switch (etapeLog.getType()) {
                case FICHE_EVALUEE_N1 -> {
                    etape.setLibelle("Évaluation saisie par le N+1");
                    etape.setNote(fiche.getNoteN1());
                    etape.setCommentaire(fiche.getCommentaireN1());
                    etape.setIcone("clipboard-check");
                    etape.setCouleur("#0284C7");
                }
                case FICHE_VALIDEE_N2 -> {
                    etape.setLibelle("Validée par le N+2");
                    etape.setDecision("ACCEPTEE");
                    etape.setCommentaire(fiche.getCommentaireN2());
                    etape.setIcone("check-circle");
                    etape.setCouleur("#16A34A");
                }
                case FICHE_REFUSEE_N2 -> {
                    etape.setLibelle("Refusée par le N+2");
                    etape.setDecision("REFUSEE");
                    etape.setCommentaire(fiche.getCommentaireN2());
                    etape.setIcone("x-circle");
                    etape.setCouleur("#DC2626");
                }
                case FICHE_ACCEPTEE_EMPLOYE -> {
                    etape.setLibelle("Acceptée par l'employé");
                    etape.setDecision("ACCEPTEE");
                    etape.setCommentaire(fiche.getCommentaireEmploye());
                    etape.setNote(fiche.getNoteFinale());
                    etape.setIcone("thumbs-up");
                    etape.setCouleur("#16A34A");
                }
                case FICHE_REFUSEE_EMPLOYE -> {
                    etape.setLibelle("Refusée par l'employé");
                    etape.setDecision("REFUSEE");
                    etape.setCommentaire(fiche.getCommentaireEmploye());
                    etape.setIcone("thumbs-down");
                    etape.setCouleur("#F59E0B");
                }
                default -> etape.setLibelle(etapeLog.getDescription());
            }
            dto.getEtapes().add(etape);
        }

        if (fiche.getStatut() == StatutFiche.CLOTUREE) {
            ActiviteDetailDTO.EtapeDTO fin = new ActiviteDetailDTO.EtapeDTO();
            fin.setLibelle("Évaluation clôturée");
            fin.setNote(fiche.getNoteFinale());
            fin.setIcone("check-circle");
            fin.setCouleur("#16A34A");
            dto.getEtapes().add(fin);
        }
    }

    private void buildEmployeDetail(ActiviteDetailDTO dto, ActiviteLog activite) {
        Employe employe = employeRepository.findById(activite.getEntiteId()).orElse(null);
        if (employe == null) {
            markSupprimee(dto, activite, "Cet employé a été supprimé");
            return;
        }

        dto.setTitre(fullName(employe));
        dto.setSousTitre(employe.getMatricule() + " · " + employe.getRole().name());
        dto.setStatut(Boolean.TRUE.equals(employe.getActif()) ? "ACTIF" : "INACTIF");

        addInfo(dto, "Matricule", employe.getMatricule());
        addInfo(dto, "Email", employe.getEmail());
        addInfo(dto, "Rôle", employe.getRole().name());
        addInfo(dto, "Responsable N+1", fullName(employe.getN1()));
        addInfo(dto, "Responsable N+2", fullName(employe.getN2()));

        for (ActiviteLog etapeLog : historique(activite)) {
            ActiviteDetailDTO.EtapeDTO etape = baseEtape(etapeLog);
            etape.setLibelle(etapeLog.getDescription());
            switch (etapeLog.getType()) {
                case EMPLOYE_CREE -> { etape.setIcone("user-plus"); etape.setCouleur("#8B0000"); }
                case EMPLOYE_MODIFIE -> { etape.setIcone("user-cog"); etape.setCouleur("#7C3AED"); }
                default -> { etape.setIcone("activity"); etape.setCouleur("#8B0000"); }
            }
            dto.getEtapes().add(etape);
        }
    }

    private void buildCampagneDetail(ActiviteDetailDTO dto, ActiviteLog activite) {
        Evaluation evaluation = evaluationRepository.findById(activite.getEntiteId()).orElse(null);
        if (evaluation == null) {
            markSupprimee(dto, activite, "Cette campagne a été supprimée");
            return;
        }

        dto.setTitre(evaluation.getNomEvaluation());
        dto.setSousTitre(evaluation.getDateDebut().format(DATE_FMT) + " → " + evaluation.getDateFin().format(DATE_FMT));
        dto.setStatut(evaluation.getStatut().name());

        addInfo(dto, "Début", evaluation.getDateDebut().format(DATE_FMT));
        addInfo(dto, "Fin", evaluation.getDateFin().format(DATE_FMT));
        addInfo(dto, "Questions", String.valueOf(evaluation.getQuestions().size()));
        addInfo(dto, "Fiches", String.valueOf(ficheRepository.findByEvaluationId(evaluation.getId()).size()));

        for (ActiviteLog etapeLog : historique(activite)) {
            ActiviteDetailDTO.EtapeDTO etape = baseEtape(etapeLog);
            etape.setLibelle(etapeLog.getDescription());
            switch (etapeLog.getType()) {
                case CAMPAGNE_CREEE -> { etape.setIcone("megaphone"); etape.setCouleur("#16A34A"); }
                case CAMPAGNE_OUVERTE -> { etape.setIcone("rocket"); etape.setCouleur("#16A34A"); }
                case CAMPAGNE_FERMEE -> { etape.setIcone("lock"); etape.setCouleur("#F59E0B"); }
                case CAMPAGNE_CLOTUREE -> { etape.setIcone("check-circle"); etape.setCouleur("#0284C7"); }
                case QUESTION_AJOUTEE -> { etape.setIcone("list-plus"); etape.setCouleur("#0284C7"); }
                default -> { etape.setIcone("activity"); etape.setCouleur("#8B0000"); }
            }
            dto.getEtapes().add(etape);
        }
    }

    // ===================== Helpers =====================

    private List<ActiviteLog> historique(ActiviteLog activite) {
        return activiteLogRepository.findByEntiteTypeAndEntiteIdOrderByCreatedAtAsc(
                activite.getEntiteType(), activite.getEntiteId());
    }

    private ActiviteDetailDTO.EtapeDTO baseEtape(ActiviteLog source) {
        ActiviteDetailDTO.EtapeDTO etape = new ActiviteDetailDTO.EtapeDTO();
        etape.setDate(source.getCreatedAt());
        Employe acteur = source.getActeur();
        if (acteur != null) {
            etape.setActeur(fullName(acteur));
            etape.setActeurRole(acteur.getRole().name());
        }
        return etape;
    }

    private void markSupprimee(ActiviteDetailDTO dto, ActiviteLog activite, String message) {
        dto.setTitre(activite.getDescription());
        dto.setSousTitre(message);
        dto.setEntiteSupprimee(true);
    }

    private void addInfo(ActiviteDetailDTO dto, String label, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            dto.getInfos().add(new ActiviteDetailDTO.InfoDTO(label, valeur));
        }
    }

    private String fullName(Employe employe) {
        return employe != null ? employe.getPrenom() + " " + employe.getNom() : null;
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
