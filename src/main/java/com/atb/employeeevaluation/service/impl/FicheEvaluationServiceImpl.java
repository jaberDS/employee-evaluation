package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.EvaluationN1Request;
import com.atb.employeeevaluation.dto.FicheEvaluationDTO;
import com.atb.employeeevaluation.dto.ValidationN2Request;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.atb.employeeevaluation.entity.Question;
import com.atb.employeeevaluation.enums.Decision;
import com.atb.employeeevaluation.enums.StatutFiche;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.exception.UnauthorizedOperationException;
import com.atb.employeeevaluation.mapper.FicheEvaluationMapper;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import com.atb.employeeevaluation.repository.FicheEvaluationRepository;
import com.atb.employeeevaluation.repository.QuestionRepository;
import com.atb.employeeevaluation.service.FicheEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FicheEvaluationServiceImpl implements FicheEvaluationService {

    private final FicheEvaluationRepository ficheRepository;
    private final EmployeRepository employeRepository;
    private final EvaluationRepository evaluationRepository;
    private final QuestionRepository questionRepository;
    private final FicheEvaluationMapper ficheMapper;
    private final ObjectMapper objectMapper;

    @Override
    public FicheEvaluationDTO evaluerParN1(EvaluationN1Request request) {
        log.info("Évaluation N+1 pour employé {} campagne {}", request.getEmployeId(), request.getEvaluationId());

        // 1. Vérifier que l'employé existe
        Employe employe = employeRepository.findById(request.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employé non trouvé avec id: " + request.getEmployeId()));

        // 2. Vérifier que la campagne existe
        Evaluation evaluation = evaluationRepository.findById(request.getEvaluationId())
                .orElseThrow(() -> new ResourceNotFoundException("Campagne non trouvée avec id: " + request.getEvaluationId()));

        // 3. Vérifier que la campagne est OUVERTE
        if (!evaluation.getStatut().name().equals("OUVERTE")) {
            throw new UnauthorizedOperationException(
                    "La campagne n'est pas ouverte pour évaluation. Statut actuel: " + evaluation.getStatut()
            );
        }

        // 4. Récupérer ou créer la fiche
        FicheEvaluation fiche = ficheRepository.findByEmployeIdAndEvaluationId(
                request.getEmployeId(),
                request.getEvaluationId()
        ).orElse(
                FicheEvaluation.builder()
                        .employe(employe)
                        .evaluation(evaluation)
                        .statut(StatutFiche.EN_COURS_N1)
                        .build()
        );

        // 5. Vérifier que la fiche peut être modifiée
        if (fiche.getStatut() == StatutFiche.CLOTUREE) {
            throw new UnauthorizedOperationException("Cette évaluation est déjà clôturée");
        }

        // 6. Valider les réponses
        Map<Long, Integer> reponses = request.getReponses();
        List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(
                request.getEvaluationId()
        );

        // Vérifier que toutes les questions ont une réponse
        for (Question q : questions) {
            if (!reponses.containsKey(q.getId())) {
                throw new UnauthorizedOperationException(
                        "Question " + q.getId() + " (" + q.getLibelle() + ") non répondue"
                );
            }
            Integer note = reponses.get(q.getId());
            if (note < 0 || note > q.getNoteMax()) {
                throw new UnauthorizedOperationException(
                        "Note invalide pour la question " + q.getId() + ". Note max: " + q.getNoteMax()
                );
            }
        }

        // 7. Calculer la note N1 (moyenne)
        double total = reponses.values().stream().mapToInt(Integer::intValue).sum();
        double noteN1 = Math.round((total / (double) questions.size()) * 100.0) / 100.0;

        // 8. Sauvegarder les réponses en JSON
        try {
            String reponsesJson = objectMapper.writeValueAsString(reponses);
            fiche.setReponsesN1(reponsesJson);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la conversion des réponses en JSON", e);
        }

        fiche.setNoteN1(noteN1);
        fiche.setCommentaireN1(request.getCommentaire());
        fiche.setStatut(StatutFiche.EN_ATTENTE_N2);

        fiche = ficheRepository.save(fiche);
        log.info("Évaluation N+1 terminée. Fiche ID: {}, Note: {}", fiche.getId(), noteN1);

        return ficheMapper.toDto(fiche);
    }

    @Override
    public FicheEvaluationDTO validerParN2(Long ficheId, ValidationN2Request request) {
        log.info("Validation N+2 pour fiche {}", ficheId);

        FicheEvaluation fiche = getEntityById(ficheId);

        // Vérifier que la fiche est en attente N+2
        if (fiche.getStatut() != StatutFiche.EN_ATTENTE_N2) {
            throw new UnauthorizedOperationException(
                    "La fiche n'est pas en attente de validation N+2. Statut actuel: " + fiche.getStatut()
            );
        }

        // Enregistrer la décision du N+2
        fiche.setDecisionN2(request.getAccepte() ? Decision.ACCEPTEE : Decision.REFUSEE);
        fiche.setCommentaireN2(request.getCommentaire());

        if (request.getAccepte()) {
            // Si N+2 accepte, passer à l'employé
            fiche.setStatut(StatutFiche.EN_ATTENTE_EMPLOYE);
            log.info("N+2 a ACCEPTÉ la fiche {}", ficheId);
        } else {
            // Si N+2 refuse, passage en révision
            fiche.setStatut(StatutFiche.A_REVISER);
            log.info("N+2 a REFUSÉ la fiche {}", ficheId);
        }

        fiche = ficheRepository.save(fiche);
        return ficheMapper.toDto(fiche);
    }

    @Override
    public FicheEvaluationDTO validerParEmploye(Long ficheId, boolean accepte) {
        log.info("Validation employé pour fiche {} - Accepte: {}", ficheId, accepte);

        FicheEvaluation fiche = getEntityById(ficheId);

        // Vérifier que la fiche est en attente employé
        if (fiche.getStatut() != StatutFiche.EN_ATTENTE_EMPLOYE) {
            throw new UnauthorizedOperationException(
                    "La fiche n'est pas en attente de validation employé. Statut actuel: " + fiche.getStatut()
            );
        }

        // Enregistrer la décision de l'employé
        fiche.setDecisionEmploye(accepte ? Decision.ACCEPTEE : Decision.REFUSEE);

        if (accepte) {
            // Si l'employé accepte, clôture définitive
            fiche.setStatut(StatutFiche.CLOTUREE);
            fiche.setNoteFinale(fiche.getNoteN1());
            log.info("Employé a ACCEPTÉ - Fiche {} clôturée avec note {}", ficheId, fiche.getNoteN1());
        } else {
            // Si l'employé refuse, passage en révision
            fiche.setStatut(StatutFiche.A_REVISER);
            log.info("Employé a REFUSÉ - Fiche {} en révision", ficheId);
        }

        fiche = ficheRepository.save(fiche);
        return ficheMapper.toDto(fiche);
    }

    @Override
    public FicheEvaluationDTO getFicheById(Long id) {
        return ficheMapper.toDto(getEntityById(id));
    }

    @Override
    public List<FicheEvaluationDTO> getFichesByEmploye(Long employeId) {
        // Vérifier que l'employé existe
        if (!employeRepository.existsById(employeId)) {
            throw new ResourceNotFoundException("Employé non trouvé avec id: " + employeId);
        }

        return ficheRepository.findByEmployeId(employeId).stream()
                .map(ficheMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<FicheEvaluationDTO> getFichesByEvaluation(Long evaluationId) {
        // Vérifier que la campagne existe
        if (!evaluationRepository.existsById(evaluationId)) {
            throw new ResourceNotFoundException("Campagne non trouvée avec id: " + evaluationId);
        }

        return ficheRepository.findByEvaluationId(evaluationId).stream()
                .map(ficheMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<FicheEvaluationDTO> getFichesByStatut(StatutFiche statut) {
        return ficheRepository.findByStatut(statut).stream()
                .map(ficheMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void recalculerNoteFinale(Long ficheId) {
        FicheEvaluation fiche = getEntityById(ficheId);

        if (fiche.getStatut() == StatutFiche.CLOTUREE) {
            // Recalculer selon les règles métier
            // Pour l'instant, on garde la note N1
            fiche.setNoteFinale(fiche.getNoteN1());
            ficheRepository.save(fiche);
        }
    }

    private FicheEvaluation getEntityById(Long id) {
        return ficheRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche d'évaluation non trouvée avec id: " + id));
    }
}