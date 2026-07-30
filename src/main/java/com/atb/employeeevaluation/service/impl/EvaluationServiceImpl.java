package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.EvaluationDTO;
import com.atb.employeeevaluation.dto.QuestionDTO;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.entity.Question;
import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.enums.TypeAffectation;
import com.atb.employeeevaluation.enums.TypeEntite;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.exception.UnauthorizedOperationException;
import com.atb.employeeevaluation.mapper.EvaluationMapper;
import com.atb.employeeevaluation.mapper.QuestionMapper;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import com.atb.employeeevaluation.repository.QuestionRepository;
import com.atb.employeeevaluation.service.ActiviteLogService;
import com.atb.employeeevaluation.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final QuestionRepository questionRepository;
    private final EvaluationMapper evaluationMapper;
    private final QuestionMapper questionMapper;
    private final ActiviteLogService activiteLogService;

    // ===================== CRUD Evaluation =====================

    @Override
    public EvaluationDTO createEvaluation(EvaluationDTO dto) {
        // Vérifier que la date de début est avant la date de fin
        if (dto.getDateDebut().isAfter(dto.getDateFin())) {
            throw new RuntimeException("La date de début doit être avant la date de fin");
        }

        Evaluation evaluation = evaluationMapper.toEntity(dto);
        evaluation.setStatut(StatutCampagne.BROUILLON);
        evaluation = evaluationRepository.save(evaluation);
        activiteLogService.log(TypeActivite.CAMPAGNE_CREEE,
                "Nouvelle campagne créée — " + evaluation.getNomEvaluation(),
                TypeEntite.CAMPAGNE, evaluation.getId());
        return evaluationMapper.toDto(evaluation);
    }

    /**
     * Crée en une seule opération le couple de campagnes Agence + Siège.
     * <p>
     * Les deux populations de la banque sont évaluées sur des référentiels
     * distincts : une campagne annuelle se décline donc toujours en deux
     * campagnes jumelles, chacune recevant ensuite ses propres questions.
     * L'opération est atomique — on ne veut jamais d'une moitié de couple.
     *
     * @return les deux campagnes créées, Agence en premier
     */
    @Override
    public List<EvaluationDTO> createEvaluationPaire(EvaluationDTO dto) {
        if (dto.getDateDebut().isAfter(dto.getDateFin())) {
            throw new RuntimeException("La date de début doit être avant la date de fin");
        }

        List<EvaluationDTO> creees = new ArrayList<>();
        for (TypeAffectation type : List.of(TypeAffectation.AGENCE, TypeAffectation.SIEGE)) {
            Evaluation evaluation = evaluationMapper.toEntity(dto);
            evaluation.setId(null);
            evaluation.setTypeAffectation(type);
            evaluation.setNomEvaluation(nomPourType(dto.getNomEvaluation(), type));
            evaluation.setStatut(StatutCampagne.BROUILLON);

            evaluation = evaluationRepository.save(evaluation);
            activiteLogService.log(TypeActivite.CAMPAGNE_CREEE,
                    "Nouvelle campagne créée — " + evaluation.getNomEvaluation(),
                    TypeEntite.CAMPAGNE, evaluation.getId());
            creees.add(evaluationMapper.toDto(evaluation));
        }
        return creees;
    }

    /** Suffixe le nom de base par la population ciblée, sans doubler un suffixe déjà saisi. */
    private String nomPourType(String nomBase, TypeAffectation type) {
        String base = nomBase == null ? "" : nomBase.trim();
        String suffixe = type == TypeAffectation.AGENCE ? "Agence" : "Siège";
        if (base.toLowerCase().endsWith(suffixe.toLowerCase())) {
            return base;
        }
        return base + " — " + suffixe;
    }

    @Override
    public EvaluationDTO updateEvaluation(Long id, EvaluationDTO dto) {
        Evaluation evaluation = getEntityById(id);

        // Vérifier que la campagne est en BROUILLON
        if (evaluation.getStatut() != StatutCampagne.BROUILLON) {
            throw new UnauthorizedOperationException(
                    "Impossible de modifier une campagne déjà ouverte ou clôturée. Statut actuel: " + evaluation.getStatut()
            );
        }

        // Vérifier que la date de début est avant la date de fin
        if (dto.getDateDebut().isAfter(dto.getDateFin())) {
            throw new RuntimeException("La date de début doit être avant la date de fin");
        }

        evaluation.setNomEvaluation(dto.getNomEvaluation());
        evaluation.setDateDebut(dto.getDateDebut());
        evaluation.setDateFin(dto.getDateFin());
        if (dto.getTypeAffectation() != null) {
            evaluation.setTypeAffectation(dto.getTypeAffectation());
        }

        evaluation = evaluationRepository.save(evaluation);
        return evaluationMapper.toDto(evaluation);
    }

    @Override
    public EvaluationDTO getEvaluationById(Long id) {
        return evaluationMapper.toDto(getEntityById(id));
    }

    @Override
    public List<EvaluationDTO> getAllEvaluations() {
        return evaluationRepository.findAll().stream()
                .map(evaluationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvaluationDTO> getEvaluationsByTypeAffectation(TypeAffectation typeAffectation) {
        return evaluationRepository.findByTypeAffectation(typeAffectation).stream()
                .map(evaluationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteEvaluation(Long id) {
        Evaluation evaluation = getEntityById(id);

        // Vérifier que la campagne est en BROUILLON
        if (evaluation.getStatut() != StatutCampagne.BROUILLON) {
            throw new UnauthorizedOperationException(
                    "Impossible de supprimer une campagne déjà ouverte ou clôturée. Statut actuel: " + evaluation.getStatut()
            );
        }

        evaluationRepository.deleteById(id);
    }

    // ===================== Gestion des statuts =====================

    @Override
    public void ouvrirCampagne(Long evaluationId) {
        Evaluation evaluation = getEntityById(evaluationId);

        // Vérifier que la campagne est en BROUILLON
        if (evaluation.getStatut() != StatutCampagne.BROUILLON) {
            throw new UnauthorizedOperationException(
                    "Seules les campagnes en BROUILLON peuvent être ouvertes. Statut actuel: " + evaluation.getStatut()
            );
        }

        // Vérifier qu'il y a des questions
        if (evaluation.getQuestions().isEmpty()) {
            throw new UnauthorizedOperationException(
                    "Impossible d'ouvrir une campagne sans questions. Ajoutez au moins une question."
            );
        }

        evaluation.setStatut(StatutCampagne.OUVERTE);
        evaluationRepository.save(evaluation);
        activiteLogService.log(TypeActivite.CAMPAGNE_OUVERTE,
                "Campagne lancée — " + evaluation.getNomEvaluation(),
                TypeEntite.CAMPAGNE, evaluation.getId());
    }

    @Override
    public void fermerCampagne(Long evaluationId) {
        Evaluation evaluation = getEntityById(evaluationId);

        // Vérifier que la campagne est OUVERTE
        if (evaluation.getStatut() != StatutCampagne.OUVERTE) {
            throw new UnauthorizedOperationException(
                    "Seules les campagnes OUVERTES peuvent être fermées. Statut actuel: " + evaluation.getStatut()
            );
        }

        evaluation.setStatut(StatutCampagne.FERMEE);
        evaluationRepository.save(evaluation);
        activiteLogService.log(TypeActivite.CAMPAGNE_FERMEE,
                "Campagne fermée — " + evaluation.getNomEvaluation(),
                TypeEntite.CAMPAGNE, evaluation.getId());
    }

    @Override
    public void cloturerCampagne(Long evaluationId) {
        Evaluation evaluation = getEntityById(evaluationId);

        // Vérifier que la campagne est FERMEE
        if (evaluation.getStatut() != StatutCampagne.FERMEE) {
            throw new UnauthorizedOperationException(
                    "Seules les campagnes FERMEES peuvent être clôturées. Statut actuel: " + evaluation.getStatut()
            );
        }

        evaluation.setStatut(StatutCampagne.CLOTUREE);
        evaluationRepository.save(evaluation);
        activiteLogService.log(TypeActivite.CAMPAGNE_CLOTUREE,
                "Campagne clôturée — " + evaluation.getNomEvaluation(),
                TypeEntite.CAMPAGNE, evaluation.getId());
    }

    // ===================== Gestion des questions =====================

    private static final int MAX_QUESTIONS = 10;

    @Override
    public QuestionDTO addQuestion(Long evaluationId, QuestionDTO questionDTO) {
        Evaluation evaluation = getEntityById(evaluationId);

        // Vérifier que la campagne est en BROUILLON
        if (evaluation.getStatut() != StatutCampagne.BROUILLON) {
            throw new UnauthorizedOperationException(
                    "Impossible d'ajouter une question à une campagne déjà ouverte. Statut actuel: " + evaluation.getStatut()
            );
        }

        // Vérifier la limite de 10 questions
        long questionCount = questionRepository.countByEvaluationId(evaluationId);
        if (questionCount >= MAX_QUESTIONS) {
            throw new UnauthorizedOperationException(
                    "Cette campagne a atteint la limite maximale de " + MAX_QUESTIONS + " questions."
            );
        }

        // Vérifier l'unicité de l'ordre
        if (questionRepository.existsByEvaluationIdAndOrdre(evaluationId, questionDTO.getOrdre())) {
            throw new RuntimeException(
                    "Une question avec l'ordre " + questionDTO.getOrdre() + " existe déjà dans cette campagne."
            );
        }

        Question question = questionMapper.toEntity(questionDTO);
        question.setEvaluation(evaluation);

        // Calculer l'ordre automatiquement si non fourni ou 0
        if (question.getOrdre() == null || question.getOrdre() == 0) {
            int maxOrdre = evaluation.getQuestions().stream()
                    .mapToInt(Question::getOrdre)
                    .max()
                    .orElse(0);
            question.setOrdre(maxOrdre + 1);
        }

        question = questionRepository.save(question);
        return questionMapper.toDto(question);
    }

    @Override
    public QuestionDTO updateQuestion(Long questionId, QuestionDTO questionDTO) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question non trouvée avec id: " + questionId));

        // Vérifier que la campagne est en BROUILLON
        if (question.getEvaluation().getStatut() != StatutCampagne.BROUILLON) {
            throw new UnauthorizedOperationException(
                    "Impossible de modifier une question d'une campagne déjà ouverte."
            );
        }

        Long evaluationId = question.getEvaluation().getId();

        // Vérifier unicité de l'ordre (ignorer la question elle-même)
        if (!question.getOrdre().equals(questionDTO.getOrdre()) &&
            questionRepository.existsByEvaluationIdAndOrdreAndIdNot(evaluationId, questionDTO.getOrdre(), questionId)) {
            throw new RuntimeException(
                    "Une question avec l'ordre " + questionDTO.getOrdre() + " existe déjà dans cette campagne."
            );
        }

        question.setLibelle(questionDTO.getLibelle());
        question.setDescription(questionDTO.getDescription());
        question.setNoteMax(questionDTO.getNoteMax() != null ? questionDTO.getNoteMax() : question.getNoteMax());
        question.setOrdre(questionDTO.getOrdre());
        question.setTypeQuestion(questionDTO.getTypeQuestion() != null ? questionDTO.getTypeQuestion() : question.getTypeQuestion());
        question.setObligatoire(questionDTO.getObligatoire() != null ? questionDTO.getObligatoire() : question.getObligatoire());
        if (questionDTO.getActif() != null) {
            question.setActif(questionDTO.getActif());
        }

        question = questionRepository.save(question);
        return questionMapper.toDto(question);
    }

    @Override
    public void removeQuestion(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question non trouvée avec id: " + questionId));

        // Vérifier que la campagne est en BROUILLON
        if (question.getEvaluation().getStatut() != StatutCampagne.BROUILLON) {
            throw new UnauthorizedOperationException(
                    "Impossible de supprimer une question d'une campagne déjà ouverte."
            );
        }

        questionRepository.deleteById(questionId);
    }

    @Override
    public QuestionDTO toggleActif(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question non trouvée avec id: " + questionId));

        question.setActif(!question.getActif());
        question = questionRepository.save(question);
        return questionMapper.toDto(question);
    }

    @Override
    public QuestionDTO getQuestionById(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question non trouvée avec id: " + questionId));
        return questionMapper.toDto(question);
    }

    @Override
    public List<QuestionDTO> getQuestionsByEvaluation(Long evaluationId) {
        getEntityById(evaluationId); // validate evaluation exists
        return questionRepository.findByEvaluationIdOrderByOrdreAsc(evaluationId)
                .stream()
                .map(questionMapper::toDto)
                .collect(Collectors.toList());
    }

    // ===================== Méthodes privées =====================

    private Evaluation getEntityById(Long id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campagne non trouvée avec id: " + id));
    }
}