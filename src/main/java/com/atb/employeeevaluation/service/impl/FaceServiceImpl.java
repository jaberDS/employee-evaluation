package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.FaceChallengeResponse;
import com.atb.employeeevaluation.dto.FaceStatusDTO;
import com.atb.employeeevaluation.dto.FaceVerifyRequest;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.entity.FaceCeremony;
import com.atb.employeeevaluation.entity.FaceTemplate;
import com.atb.employeeevaluation.enums.LivenessAction;
import com.atb.employeeevaluation.enums.MfaPurpose;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.exception.FaceVerificationException;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.exception.UnauthorizedOperationException;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.FaceCeremonyRepository;
import com.atb.employeeevaluation.repository.FaceTemplateRepository;
import com.atb.employeeevaluation.security.FaceRecognitionClient;
import com.atb.employeeevaluation.service.ActiviteLogService;
import com.atb.employeeevaluation.service.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Reconnaissance faciale comme second facteur.
 *
 * Le microservice Python fournit les vecteurs et le verdict de vivacité ; la
 * décision d'identité est prise ici, en Java, avec le reste de la logique
 * d'authentification. Le seuil de similarité n'est donc jamais exposé au réseau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FaceServiceImpl implements FaceService {

    /** Une consigne neutre en ouverture, puis deux consignes tirées au sort. */
    private static final List<LivenessAction> CONSIGNES_TIRABLES = List.of(
            LivenessAction.BLINK,
            LivenessAction.TURN_LEFT,
            LivenessAction.TURN_RIGHT,
            LivenessAction.SMILE);

    private static final int NB_CONSIGNES = 2;
    private static final int DIMENSIONS_ATTENDUES = 512;

    private final FaceRecognitionClient faceClient;
    private final FaceTemplateRepository templateRepository;
    private final FaceCeremonyRepository ceremonyRepository;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActiviteLogService activiteLogService;

    private final SecureRandom random = new SecureRandom();

    /**
     * Similarité cosinus minimale. ArcFace R100 sépare nettement : 0,42 laisse
     * passer les variations d'éclairage et de lunettes sans ouvrir aux sosies.
     * À recalibrer sur la population réelle avant mise en production.
     */
    @Value("${face.match-threshold:0.42}")
    private double seuilCorrespondance;

    /** Qualité minimale pour accepter une inscription — pas pour la vérifier. */
    @Value("${face.min-enroll-quality:0.45}")
    private double qualiteMinimale;

    @Value("${face.ceremony-timeout-ms:180000}")
    private long ceremonyTimeoutMs;

    // ─── Cérémonie ────────────────────────────────────────────────────────────

    @Override
    public FaceChallengeResponse startCeremony(String matricule, MfaPurpose purpose, boolean decoy) {
        List<LivenessAction> sequence = tirerSequence();

        String ceremonyId = UUID.randomUUID().toString();
        ceremonyRepository.save(FaceCeremony.builder()
                .ceremonyId(ceremonyId)
                .matricule(matricule)
                .purpose(purpose)
                .actions(sequence.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""))
                .decoy(decoy)
                .expireLe(LocalDateTime.now().plusNanos(ceremonyTimeoutMs * 1_000_000))
                .consomme(false)
                .build());

        List<FaceChallengeResponse.Step> etapes = sequence.stream()
                .map(a -> FaceChallengeResponse.Step.builder()
                        .action(a.name())
                        .instruction(a.getInstruction())
                        .dureeMs(a.getDureeMs())
                        .build())
                .toList();

        return FaceChallengeResponse.builder().ceremonyId(ceremonyId).steps(etapes).build();
    }

    /**
     * Séquence imprévisible : c'est elle qui bloque le rejeu d'une vidéo. On
     * ouvre par une trame neutre, qui sert de référence d'identité.
     */
    private List<LivenessAction> tirerSequence() {
        List<LivenessAction> pool = new ArrayList<>(CONSIGNES_TIRABLES);
        List<LivenessAction> sequence = new ArrayList<>();
        sequence.add(LivenessAction.NEUTRAL);
        for (int i = 0; i < NB_CONSIGNES && !pool.isEmpty(); i++) {
            sequence.add(pool.remove(random.nextInt(pool.size())));
        }
        return sequence;
    }

    // ─── Inscription ──────────────────────────────────────────────────────────

    @Override
    public FaceStatusDTO enroll(String matricule, FaceVerifyRequest request) {
        FaceCeremony ceremony = consommer(request.getCeremonyId(), matricule, MfaPurpose.ENROLLMENT);
        Employe employe = getEmploye(matricule);

        FaceRecognitionClient.LivenessResult resultat = analyser(ceremony, request);

        if (resultat.getQuality() < qualiteMinimale) {
            throw new FaceVerificationException(
                    "Qualité d'image insuffisante. Placez-vous face à une source de lumière et recommencez.");
        }

        float[] vecteur = versTableau(resultat.getEmbedding());

        // Un employé n'a qu'un gabarit : on écrase le précédent s'il existe.
        FaceTemplate gabarit = templateRepository.findByEmployeMatriculeAndActifTrue(matricule)
                .orElseGet(() -> FaceTemplate.builder().employe(employe).build());

        gabarit.setEmbedding(encoder(vecteur));
        gabarit.setDimensions(vecteur.length);
        gabarit.setQualite(resultat.getQuality());
        gabarit.setModele("buffalo_l");
        gabarit.setActif(true);
        templateRepository.save(gabarit);

        activiteLogService.log(TypeActivite.FACE_ID_ENREGISTRE,
                "Reconnaissance faciale activée — " + matricule);

        return FaceStatusDTO.builder()
                .enrolled(true)
                .qualite(gabarit.getQualite())
                .creeLe(gabarit.getCreeLe())
                .build();
    }

    // ─── Vérification ─────────────────────────────────────────────────────────

    @Override
    public String verify(String matricule, FaceVerifyRequest request, MfaPurpose purpose) {
        FaceCeremony ceremony = consommer(request.getCeremonyId(), matricule, purpose);

        FaceRecognitionClient.LivenessResult resultat = analyser(ceremony, request);

        // La cérémonie leurre va jusqu'au bout puis échoue : de l'extérieur, elle
        // est indiscernable d'un visage qui ne correspond pas.
        if (Boolean.TRUE.equals(ceremony.getDecoy())) {
            throw new FaceVerificationException("Le visage ne correspond pas à ce compte");
        }

        FaceTemplate gabarit = templateRepository.findByEmployeMatriculeAndActifTrue(matricule)
                .orElseThrow(() -> new FaceVerificationException(
                        "Aucun visage enregistré sur ce compte"));

        double similarite = cosinus(decoder(gabarit.getEmbedding()), versTableau(resultat.getEmbedding()));
        log.debug("Similarité faciale pour {} : {}", matricule, similarite);

        if (similarite < seuilCorrespondance) {
            activiteLogService.log(TypeActivite.MFA_ECHOUEE,
                    "Échec de reconnaissance faciale — " + matricule);
            throw new FaceVerificationException("Le visage ne correspond pas à ce compte");
        }

        gabarit.setDerniereUtilisation(LocalDateTime.now());
        templateRepository.save(gabarit);
        return matricule;
    }

    /**
     * Délègue au service Python, puis vérifie que les consignes exécutées sont
     * bien celles qui avaient été demandées. Se fier au champ `action` envoyé par
     * le client sans le confronter à la cérémonie viderait la vivacité de son sens.
     *
     * Le client envoie une rafale de trames par consigne : on compare donc la
     * suite des consignes DISTINCTES, dans l'ordre, et non chaque trame.
     */
    private FaceRecognitionClient.LivenessResult analyser(FaceCeremony ceremony, FaceVerifyRequest request) {
        List<String> attendues = Arrays.asList(ceremony.getActions().split(","));
        List<String> recues = consignesDistinctes(request);

        if (!attendues.equals(recues)) {
            log.info("Séquence refusée pour {} — attendu {}, reçu {}",
                    ceremony.getMatricule(), attendues, recues);
            throw new FaceVerificationException("Séquence de vérification invalide. Recommencez.");
        }

        FaceRecognitionClient.LivenessResult resultat = faceClient.verifyLiveness(request.getFrames());

        if (resultat == null || resultat.getEmbedding() == null || resultat.getEmbedding().isEmpty()) {
            throw new FaceVerificationException("Aucun visage exploitable. Recadrez et recommencez.");
        }
        if (!resultat.isLive()) {
            activiteLogService.log(TypeActivite.MFA_ECHOUEE,
                    "Échec de vivacité faciale — " + ceremony.getMatricule());
            throw new FaceVerificationException(messageVivacite(resultat));
        }
        if (resultat.getEmbedding().size() != DIMENSIONS_ATTENDUES) {
            throw new IllegalStateException(
                    "Dimension d'embedding inattendue : " + resultat.getEmbedding().size());
        }
        return resultat;
    }

    /**
     * Consignes reçues, dédoublonnées à la volée : une rafale de huit trames
     * BLINK compte pour une seule consigne BLINK.
     */
    private List<String> consignesDistinctes(FaceVerifyRequest request) {
        List<String> consignes = new ArrayList<>();
        for (var frame : request.getFrames()) {
            String action = frame.getAction().toUpperCase();
            if (consignes.isEmpty() || !consignes.get(consignes.size() - 1).equals(action)) {
                consignes.add(action);
            }
        }
        return consignes;
    }

    /**
     * Message d'échec ciblé sur la consigne ratée : « suivez les consignes »
     * n'aide personne à comprendre lequel des deux mouvements n'a pas été vu.
     */
    private String messageVivacite(FaceRecognitionClient.LivenessResult resultat) {
        if (!resultat.isIdentityConsistent()) {
            return "Le visage détecté change au cours de la séquence. "
                    + "Restez seul dans le cadre et recommencez.";
        }

        List<FaceRecognitionClient.StepVerdict> etapes =
                resultat.getSteps() == null ? List.of() : resultat.getSteps();

        String ratees = etapes.stream()
                .filter(e -> !e.isPassed())
                .map(e -> switch (e.getAction() == null ? "" : e.getAction().toUpperCase()) {
                    case "BLINK" -> "le clignement des yeux";
                    case "SMILE" -> "le sourire";
                    case "TURN_LEFT" -> "la rotation vers la gauche";
                    case "TURN_RIGHT" -> "la rotation vers la droite";
                    default -> "un mouvement";
                })
                .distinct()
                .reduce((a, b) -> a + " et " + b)
                .orElse("");

        if (ratees.isEmpty()) {
            return "Détection de vivacité échouée. Suivez les consignes affichées et recommencez.";
        }
        return "Nous n'avons pas détecté " + ratees
                + ". Exagérez le mouvement, restez bien éclairé et recommencez.";
    }

    // ─── Gestion ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public FaceStatusDTO status(String matricule) {
        return templateRepository.findByEmployeMatriculeAndActifTrue(matricule)
                .map(t -> FaceStatusDTO.builder()
                        .enrolled(true)
                        .qualite(t.getQualite())
                        .creeLe(t.getCreeLe())
                        .build())
                .orElseGet(() -> FaceStatusDTO.builder().enrolled(false).build());
    }

    @Override
    public void remove(String matricule, String currentPassword) {
        Employe employe = getEmploye(matricule);
        // Retirer un facteur affaiblit le compte : on re-authentifie l'utilisateur.
        if (!passwordEncoder.matches(currentPassword, employe.getMotDePasse())) {
            throw new UnauthorizedOperationException("Le mot de passe actuel est incorrect");
        }

        FaceTemplate gabarit = templateRepository.findByEmployeMatriculeAndActifTrue(matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun visage enregistré"));

        gabarit.setActif(false);
        templateRepository.save(gabarit);
        activiteLogService.log(TypeActivite.FACE_ID_SUPPRIME,
                "Reconnaissance faciale désactivée — " + matricule);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTemplate(String matricule) {
        return templateRepository.existsByEmployeMatriculeAndActifTrue(matricule);
    }

    // ─── Interne ──────────────────────────────────────────────────────────────

    private Employe getEmploye(String matricule) {
        return employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Employé non trouvé: " + matricule));
    }

    /** Une cérémonie ne sert qu'une fois : la consommer coupe le rejeu. */
    private FaceCeremony consommer(String ceremonyId, String matricule, MfaPurpose purpose) {
        FaceCeremony ceremony = ceremonyRepository.findByCeremonyIdAndConsommeFalse(ceremonyId)
                .orElseThrow(() -> new FaceVerificationException("Cérémonie inconnue ou déjà utilisée"));

        if (!ceremony.getMatricule().equals(matricule)) {
            throw new FaceVerificationException("Cérémonie liée à un autre compte");
        }
        if (ceremony.getPurpose() != purpose) {
            throw new FaceVerificationException("Cérémonie destinée à un autre usage");
        }
        if (ceremony.getExpireLe().isBefore(LocalDateTime.now())) {
            throw new FaceVerificationException("Cérémonie expirée. Recommencez.");
        }

        ceremony.setConsomme(true);
        return ceremonyRepository.save(ceremony);
    }

    /**
     * Similarité cosinus. Les vecteurs sont déjà normalisés côté Python, mais on
     * divise quand même par les normes : un gabarit d'une version antérieure du
     * service pourrait ne pas l'être.
     */
    private double cosinus(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalStateException("Gabarits de dimensions différentes");
        }
        double produit = 0, normeA = 0, normeB = 0;
        for (int i = 0; i < a.length; i++) {
            produit += (double) a[i] * b[i];
            normeA += (double) a[i] * a[i];
            normeB += (double) b[i] * b[i];
        }
        if (normeA == 0 || normeB == 0) {
            return 0;
        }
        return produit / (Math.sqrt(normeA) * Math.sqrt(normeB));
    }

    private float[] versTableau(List<Double> valeurs) {
        float[] vecteur = new float[valeurs.size()];
        for (int i = 0; i < valeurs.size(); i++) {
            vecteur[i] = valeurs.get(i).floatValue();
        }
        return vecteur;
    }

    /** Little-endian explicite : l'ordre ne doit pas dépendre de la machine. */
    private byte[] encoder(float[] vecteur) {
        ByteBuffer buffer = ByteBuffer.allocate(vecteur.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vecteur) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private float[] decoder(byte[] octets) {
        ByteBuffer buffer = ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN);
        float[] vecteur = new float[octets.length / Float.BYTES];
        for (int i = 0; i < vecteur.length; i++) {
            vecteur[i] = buffer.getFloat();
        }
        return vecteur;
    }
}
