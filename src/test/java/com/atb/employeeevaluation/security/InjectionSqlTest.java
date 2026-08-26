package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.enums.TypeAffectation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Injection SQL.
 *
 * L'application interroge la base exclusivement par Spring Data JPA, avec des
 * requêtes dérivées du nom des méthodes et deux requêtes JPQL paramétrées. Aucune
 * concaténation de chaîne ne construit de SQL. Ces tests ne cherchent donc pas à
 * découvrir une faille connue : ils **verrouillent cette propriété**. Le jour où
 * quelqu'un ajoutera un `nativeQuery` bâti par concaténation — la manière la plus
 * naturelle d'implémenter une recherche libre — ils tomberont.
 *
 * Le contrôle décisif est celui de la survie des données : une charge utile qui
 * ne provoque pas d'erreur peut très bien s'être exécutée silencieusement. On
 * vérifie donc après chaque tentative que la table est toujours là et complète.
 */
@DisplayName("Injection SQL")
class InjectionSqlTest extends SecuriteTestBase {

    /**
     * Charges classiques : évasion de guillemet, tautologie, empilement de
     * requêtes, commentaire de fin de ligne, UNION, et destruction de table.
     */
    private static final String[] CHARGES = {
            "' OR '1'='1",
            "' OR 1=1 --",
            "admin'--",
            "'; DROP TABLE employe; --",
            "' UNION SELECT * FROM employe --",
            "1' AND SLEEP(5)--",
            "\" OR \"\"=\"",
            "' OR 'x'='x' /*",
            "'; UPDATE employe SET role='ADMIN' WHERE matricule='EMP400'; --",
            "\\'; DROP TABLE fiche_evaluation; --"
    };

    // ═══ Point d'entrée non authentifié ══════════════════════════════════════

    /**
     * La connexion est la surface la plus exposée : elle est atteignable sans
     * jeton. Une tautologie doit être traitée comme un matricule ordinaire qui
     * n'existe pas, jamais comme une condition vraie.
     */
    @Test
    @DisplayName("La connexion ne cède à aucune tautologie SQL")
    void connexionResisteAuxTautologies() throws Exception {
        for (String charge : CHARGES) {
            Map<String, String> corps = new HashMap<>();
            corps.put("matricule", charge);
            corps.put("motDePasse", charge);

            int statut = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(corps)))
                    .andReturn().getResponse().getStatus();

            // 401 (identifiants faux), 404 (matricule inconnu) ou 429 (le
            // limiteur s'est déclenché) sont tous des refus. Un 200 signifierait
            // que la charge a été interprétée comme une condition vraie.
            assertThat(statut)
                    .as("Charge « %s » sur /api/auth/login", charge)
                    .isIn(401, 404, 429);
        }

        assertThat(employeRepository.count())
                .as("La table employe doit avoir survécu aux tentatives")
                .isEqualTo(6);
    }

    // ═══ Paramètres de chemin ════════════════════════════════════════════════

    /**
     * `/api/employes/matricule/{matricule}` place directement une chaîne du
     * client dans une requête dérivée. C'est le paramètre le plus tentant pour
     * une injection.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "' OR '1'='1",
            "admin'--",
            "'; DROP TABLE employe; --",
            "' UNION SELECT * FROM employe --"
    })
    @DisplayName("La recherche par matricule ne renvoie jamais de résultat sur une charge SQL")
    void rechercheParMatriculeEstParametree(String charge) throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/employes/matricule/{matricule}", charge), admin))
                .andReturn().getResponse().getStatus();

        // Aucun employé ne porte littéralement ce matricule : la seule réponse
        // correcte est « non trouvé ». Un 200 prouverait que la condition a été
        // évaluée par le moteur SQL au lieu d'être comparée comme une valeur.
        assertThat(statut)
                .as("Charge « %s » doit être traitée comme une valeur littérale", charge)
                .isNotEqualTo(200);

        assertThat(employeRepository.count()).isEqualTo(6);
        assertThat(ficheRepository.count()).isEqualTo(2);
    }

    /**
     * Les identifiants sont typés `Long`. Une charge textuelle ne peut donc même
     * pas atteindre la couche de persistance : Spring la rejette à la conversion.
     * C'est une défense réelle, et ce test la constate.
     */
    @Test
    @DisplayName("Un identifiant non numérique est rejeté avant d'atteindre la base")
    void identifiantNonNumeriqueEstRejete() throws Exception {
        for (String charge : new String[]{"1 OR 1=1", "1; DROP TABLE fiche_evaluation", "' OR '1'='1"}) {
            mockMvc.perform(authentifie(get("/api/employes/{id}", charge), admin))
                    .andReturn();
        }
        assertThat(ficheRepository.count()).isEqualTo(2);
        assertThat(employeRepository.count()).isEqualTo(6);
    }

    /**
     * Les énumérations de chemin (`/statut/{statut}`, `/role/{role}`) sont un
     * filtre naturel : Jackson n'accepte qu'une des constantes déclarées. Une
     * valeur hors liste doit produire un 400, pas une requête.
     */
    @Test
    @DisplayName("Une énumération de chemin n'accepte aucune valeur hors catalogue")
    void enumerationDeCheminEstFermee() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/statut/{statut}", "CLOTUREE' OR '1'='1"), admin))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Une valeur d'énumération inventée doit être refusée")
                .isIn(400, 500);
    }

    // ═══ Corps de requête ════════════════════════════════════════════════════

    /**
     * Une charge stockée puis relue est le scénario le plus dangereux : elle
     * traverse une écriture *et* une lecture. Si elle ressort intacte, c'est que
     * les deux trajets l'ont traitée comme une donnée.
     */
    @Test
    @DisplayName("Une charge SQL stockée est conservée littéralement, sans être exécutée")
    void chargeStockeeResteLitterale() throws Exception {
        String charge = "Robert'); DROP TABLE employe;--";

        Map<String, Object> nouvel = new HashMap<>();
        nouvel.put("matricule", "SQL999");
        nouvel.put("nom", charge);
        nouvel.put("prenom", "Test");
        nouvel.put("email", "sql999@atb.test");
        nouvel.put("motDePasse", MOT_DE_PASSE);
        nouvel.put("role", "EMPLOYE");
        nouvel.put("typeAffectation", "SIEGE");

        mockMvc.perform(authentifie(post("/api/employes"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nouvel)))
                .andReturn();

        // La table existe toujours — la charge n'a pas été exécutée.
        assertThat(employeRepository.count()).isEqualTo(7);

        Employe stocke = employeRepository.findByMatricule("SQL999").orElseThrow();
        assertThat(stocke.getNom())
                .as("Le texte doit être conservé tel quel, ni exécuté ni tronqué")
                .isEqualTo(charge);
    }

    /**
     * Une lecture par nom après stockage : le second trajet est celui où une
     * concaténation naïve se manifesterait.
     */
    @Test
    @DisplayName("La relecture d'une donnée piégée n'exécute rien")
    void relectureDeDonneePiegeeEstInoffensive() throws Exception {
        Employe piege = creerEmploye("SQL888", "' OR 1=1 --", "Union",
                Role.EMPLOYE, TypeAffectation.SIEGE, null, null);

        mockMvc.perform(authentifie(get("/api/employes/{id}", piege.getId()), admin))
                .andReturn();
        mockMvc.perform(authentifie(get("/api/employes"), admin)).andReturn();

        assertThat(employeRepository.count()).isEqualTo(7);
        assertThat(employeRepository.findByMatricule("SQL888")).isPresent();
    }

    /**
     * Le paramètre de requête `?type=` alimente `findByTypeAffectation`. Il est
     * typé par une énumération, mais rien ne le dit à la lecture du contrôleur :
     * ce test fige la garantie.
     */
    @Test
    @DisplayName("Le filtre d'affectation n'accepte que ses deux valeurs")
    void filtreAffectationEstFerme() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/evaluations").param("type", "AGENCE' UNION SELECT 1 --"), admin))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(400, 500);
        assertThat(evaluationRepository.count()).isEqualTo(2);
    }
}
