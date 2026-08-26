package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.entity.Evaluation;
import com.atb.employeeevaluation.entity.FicheEvaluation;
import com.atb.employeeevaluation.entity.Question;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.enums.StatutCampagne;
import com.atb.employeeevaluation.enums.StatutFiche;
import com.atb.employeeevaluation.enums.TypeAffectation;
import com.atb.employeeevaluation.enums.TypeQuestion;
import com.atb.employeeevaluation.repository.ActiviteLogRepository;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.EvaluationRepository;
import com.atb.employeeevaluation.repository.FicheEvaluationRepository;
import com.atb.employeeevaluation.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

/**
 * Socle commun aux tests de sécurité : une application réelle, une base H2, et
 * un jeu de données représentatif de la hiérarchie ATB.
 *
 * Les tests portent volontairement sur la pile HTTP complète (MockMvc + chaîne
 * de filtres Spring Security) plutôt que sur les services isolés. Une faille de
 * contrôle d'accès ne se voit pas en appelant un service directement : elle naît
 * du chemin qu'emprunte la requête — filtre JWT, matcher d'URL, contrôleur.
 * Tester la couche du dessous laisserait précisément passer ce que l'on cherche.
 *
 * Deux branches indépendantes sont montées pour que le cloisonnement soit
 * démontrable : ce que voit ALICE ne doit jamais apparaître chez BOB, et
 * inversement. Sans deux branches, un test « je vois mes données » passe même
 * quand le serveur renvoie tout à tout le monde.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class SecuriteTestBase {

    /** Mot de passe conforme à la politique (majuscule, minuscule, chiffre, spécial). */
    protected static final String MOT_DE_PASSE = "Passw0rd!2026";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtUtil jwtUtil;
    @Autowired protected PasswordEncoder passwordEncoder;

    @Autowired protected EmployeRepository employeRepository;
    @Autowired protected EvaluationRepository evaluationRepository;
    @Autowired protected FicheEvaluationRepository ficheRepository;
    @Autowired protected QuestionRepository questionRepository;
    @Autowired protected ActiviteLogRepository activiteLogRepository;

    // ─── Branche « agence » ───────────────────────────────────────────────────
    protected Employe admin;
    protected Employe n2Alice;
    protected Employe n1Alice;
    protected Employe employeAlice;
    protected Evaluation campagneAgence;
    protected FicheEvaluation ficheAlice;

    // ─── Branche « siège », étrangère à la précédente ─────────────────────────
    protected Employe n1Bob;
    protected Employe employeBob;
    protected Evaluation campagneSiege;
    protected FicheEvaluation ficheBob;

    @BeforeEach
    void preparerJeuDeDonnees() {
        // L'ordre compte : les fiches référencent employés et campagnes.
        ficheRepository.deleteAll();
        questionRepository.deleteAll();
        evaluationRepository.deleteAll();
        activiteLogRepository.deleteAll();
        employeRepository.deleteAll();

        admin = creerEmploye("ADM100", "Dupont", "Jean", Role.ADMIN, TypeAffectation.SIEGE, null, null);

        n2Alice = creerEmploye("N2A200", "Alaoui", "Nadia", Role.N2, TypeAffectation.AGENCE, null, null);
        n1Alice = creerEmploye("N1A300", "Ben Salah", "Karim", Role.N1, TypeAffectation.AGENCE, null, n2Alice);
        employeAlice = creerEmploye("EMP400", "Trabelsi", "Alice", Role.EMPLOYE,
                TypeAffectation.AGENCE, n1Alice, n2Alice);

        n1Bob = creerEmploye("N1B500", "Gharbi", "Sonia", Role.N1, TypeAffectation.SIEGE, null, null);
        employeBob = creerEmploye("EMP600", "Mansour", "Bob", Role.EMPLOYE,
                TypeAffectation.SIEGE, n1Bob, null);

        campagneAgence = creerCampagne("Campagne annuelle 2026 - Agence", TypeAffectation.AGENCE);
        campagneSiege = creerCampagne("Campagne annuelle 2026 - Siege", TypeAffectation.SIEGE);

        ficheAlice = creerFiche(employeAlice, campagneAgence, StatutFiche.EN_ATTENTE_N2, 15.5);
        ficheBob = creerFiche(employeBob, campagneSiege, StatutFiche.CLOTUREE, 12.0);
    }

    // ═══ Fabriques ═══════════════════════════════════════════════════════════

    protected Employe creerEmploye(String matricule, String nom, String prenom, Role role,
                                   TypeAffectation affectation, Employe n1, Employe n2) {
        return employeRepository.save(Employe.builder()
                .matricule(matricule)
                .nom(nom)
                .prenom(prenom)
                .email(matricule.toLowerCase() + "@atb.test")
                .motDePasse(passwordEncoder.encode(MOT_DE_PASSE))
                .role(role)
                .typeAffectation(affectation)
                .n1(n1)
                .n2(n2)
                .actif(true)
                .build());
    }

    protected Evaluation creerCampagne(String nom, TypeAffectation affectation) {
        Evaluation campagne = evaluationRepository.save(Evaluation.builder()
                .nomEvaluation(nom)
                .dateDebut(LocalDateTime.now().minusDays(5))
                .dateFin(LocalDateTime.now().plusDays(25))
                .statut(StatutCampagne.OUVERTE)
                .typeAffectation(affectation)
                .build());

        questionRepository.save(Question.builder()
                .libelle("Qualite du travail")
                .noteMax(10)
                .ordre(1)
                .typeQuestion(TypeQuestion.NOTE)
                .obligatoire(true)
                .actif(true)
                .evaluation(campagne)
                .build());

        return campagne;
    }

    protected FicheEvaluation creerFiche(Employe employe, Evaluation campagne,
                                         StatutFiche statut, Double note) {
        return ficheRepository.save(FicheEvaluation.builder()
                .employe(employe)
                .evaluation(campagne)
                .statut(statut)
                .noteN1(note)
                .noteFinale(statut == StatutFiche.CLOTUREE ? note : null)
                .build());
    }

    // ═══ Jetons ══════════════════════════════════════════════════════════════

    /** Jeton d'accès légitime, émis par la même mécanique que la connexion. */
    protected String jeton(Employe employe) {
        return jwtUtil.generateToken(employe.getMatricule());
    }

    /** Pose l'en-tête Authorization, comme le ferait le navigateur. */
    protected MockHttpServletRequestBuilder authentifie(MockHttpServletRequestBuilder requete,
                                                        Employe employe) {
        return requete.header("Authorization", "Bearer " + jeton(employe));
    }
}
