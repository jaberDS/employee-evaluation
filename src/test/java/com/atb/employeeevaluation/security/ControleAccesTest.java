package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Contrôle d'accès : IDOR et escalade de privilèges.
 *
 * C'est la catégorie de loin la plus payante ici. L'injection SQL est
 * structurellement impossible (JPA paramétré partout) et le XSS est neutralisé
 * par le rendu Angular ; en revanche l'autorisation est écrite à la main, point
 * par point, et une règle oubliée ne provoque aucune erreur visible — elle
 * ouvre simplement la porte.
 *
 * Deux mécaniques sont attaquées :
 *
 * **L'ordre des matchers.** Spring Security applique la première règle qui
 * correspond, pas la plus spécifique. Une règle large placée trop tôt rend
 * muettes toutes celles qui la suivent, sans le moindre avertissement au
 * démarrage.
 *
 * **Le contrôle de propriété.** Une vérification qui n'examine que le rôle de
 * l'appelant, sans le rapprocher de la ressource visée, laisse un responsable
 * lire l'équipe d'un autre. Les deux branches du jeu de données — agence et
 * siège, sans lien hiérarchique — servent précisément à le mesurer.
 */
@DisplayName("Contrôle d'accès")
class ControleAccesTest extends SecuriteTestBase {

    // ═══ Escalade de privilèges : agir au-dessus de son rôle ═════════════════

    /**
     * Saisir une évaluation appartient au N+1. Si un employé y parvient, il note
     * lui-même sa propre performance — et celle de n'importe qui d'autre.
     */
    @Test
    @DisplayName("Un employé ne peut pas saisir une évaluation N+1")
    void employeNePeutPasEvaluer() throws Exception {
        int statut = mockMvc.perform(authentifie(post("/api/fiches/evaluer"), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsEvaluationN1(employeAlice.getId(), campagneAgence.getId())))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Un EMPLOYE saisissant une note N+1 s'auto-évaluerait")
                .isEqualTo(403);
    }

    /** Même chose pour un N+2 : la saisie n'est pas de son ressort. */
    @Test
    @DisplayName("Un N+2 ne peut pas saisir une évaluation N+1")
    void n2NePeutPasEvaluer() throws Exception {
        int statut = mockMvc.perform(authentifie(post("/api/fiches/evaluer"), n2Alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsEvaluationN1(employeAlice.getId(), campagneAgence.getId())))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    /**
     * La validation N+2 est l'étape de contrôle du dispositif. Un employé qui la
     * franchit valide sa propre note sans qu'aucun supérieur ne l'ait vue.
     */
    @Test
    @DisplayName("Un employé ne peut pas valider sa fiche à la place du N+2")
    void employeNePeutPasValiderCommeN2() throws Exception {
        Map<String, Object> validation = Map.of("accepte", true, "commentaire", "Auto-validation");

        int statut = mockMvc.perform(authentifie(
                        patch("/api/fiches/{id}/n2", ficheAlice.getId()), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validation)))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Franchir l'étape N+2 soi-même supprime tout contrôle hiérarchique")
                .isEqualTo(403);
    }

    /** Le N+1 propose, il ne valide pas : c'est la séparation des rôles. */
    @Test
    @DisplayName("Un N+1 ne peut pas valider à la place du N+2")
    void n1NePeutPasValiderCommeN2() throws Exception {
        Map<String, Object> validation = Map.of("accepte", true, "commentaire", "OK");

        int statut = mockMvc.perform(authentifie(
                        patch("/api/fiches/{id}/n2", ficheAlice.getId()), n1Alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validation)))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    /** La gestion du personnel est réservée à l'administrateur. */
    @Test
    @DisplayName("Un employé ne peut ni créer, ni modifier, ni supprimer un compte")
    void employeNePeutPasGererLesComptes() throws Exception {
        Map<String, Object> nouveau = new HashMap<>();
        nouveau.put("matricule", "PIRATE1");
        nouveau.put("nom", "Pirate");
        nouveau.put("prenom", "Jean");
        nouveau.put("email", "pirate@atb.test");
        nouveau.put("motDePasse", MOT_DE_PASSE);
        nouveau.put("role", "ADMIN");
        nouveau.put("typeAffectation", "SIEGE");

        assertThat(mockMvc.perform(authentifie(post("/api/employes"), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nouveau)))
                .andReturn().getResponse().getStatus())
                .as("Créer un compte ADMIN depuis un compte employé").isEqualTo(403);

        assertThat(mockMvc.perform(authentifie(
                        delete("/api/employes/{id}", employeBob.getId()), employeAlice))
                .andReturn().getResponse().getStatus())
                .as("Supprimer le compte d'un collègue").isEqualTo(403);

        assertThat(employeRepository.findByMatricule("PIRATE1")).isEmpty();
        assertThat(employeRepository.count()).isEqualTo(6);
    }

    /**
     * L'escalade la plus directe : se réattribuer le rôle ADMIN sur son propre
     * enregistrement, ce qui n'exige de connaître aucun autre identifiant.
     */
    @Test
    @DisplayName("Un employé ne peut pas se promouvoir administrateur")
    void employeNePeutPasSeAutoPromouvoir() throws Exception {
        Map<String, Object> promotion = new HashMap<>();
        promotion.put("id", employeAlice.getId());
        promotion.put("matricule", employeAlice.getMatricule());
        promotion.put("nom", employeAlice.getNom());
        promotion.put("prenom", employeAlice.getPrenom());
        promotion.put("email", employeAlice.getEmail());
        promotion.put("role", "ADMIN");
        promotion.put("typeAffectation", "AGENCE");

        mockMvc.perform(authentifie(put("/api/employes/{id}", employeAlice.getId()), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promotion)))
                .andReturn();

        // Le contrôle qui compte porte sur la base, pas sur le code HTTP : une
        // réponse d'erreur après écriture ne vaudrait rien.
        assertThat(employeRepository.findByMatricule(employeAlice.getMatricule()).orElseThrow().getRole())
                .as("Le rôle ne doit pas avoir changé")
                .isEqualTo(com.atb.employeeevaluation.enums.Role.EMPLOYE);
    }

    /** Se rattacher à un autre N+1 revient à choisir qui vous évalue. */
    @Test
    @DisplayName("Un employé ne peut pas réécrire sa propre hiérarchie")
    void employeNePeutPasChangerSaHierarchie() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        patch("/api/employes/{id}/hierarchie", employeAlice.getId()), employeAlice)
                        .param("n1Id", n1Bob.getId().toString()))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);

        assertThat(employeRepository.findById(employeAlice.getId()).orElseThrow().getN1().getId())
                .as("Le rattachement hiérarchique doit être inchangé")
                .isEqualTo(n1Alice.getId());
    }

    // ═══ IDOR : atteindre la ressource d'autrui ══════════════════════════════

    /** L'IDOR canonique : incrémenter un identifiant dans l'URL. */
    @Test
    @DisplayName("Un employé ne peut pas lire la fiche d'un autre employé")
    void employeNeLitPasLaFicheDUnAutre() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/{id}", ficheBob.getId()), employeAlice))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(403, 404);
    }

    @Test
    @DisplayName("Un employé ne peut pas lister les fiches d'un autre employé")
    void employeNeListePasLesFichesDUnAutre() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/employe/{id}", employeBob.getId()), employeAlice))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(403, 404);
    }

    /**
     * Le filtre par statut ne prend aucun identifiant : il ne « ressemble » donc
     * pas à un IDOR. C'est pourtant pire — il renvoie les fiches de toute la
     * banque en une requête, à qui la demande.
     */
    @Test
    @DisplayName("Le filtre par statut ne divulgue pas les fiches de toute la banque")
    void filtreParStatutNeDivulguePasTout() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(authentifie(
                        get("/api/fiches/statut/{statut}", "CLOTUREE"), employeAlice))
                .andReturn().getResponse();

        if (reponse.getStatus() == 200) {
            var corps = objectMapper.readTree(reponse.getContentAsString());
            for (var fiche : corps) {
                assertThat(fiche.path("employeId").asLong())
                        .as("Une fiche d'autrui apparaît dans le filtre par statut")
                        .isEqualTo(employeAlice.getId());
            }
        } else {
            assertThat(reponse.getStatus()).isEqualTo(403);
        }
    }

    /**
     * Même raisonnement pour la liste par campagne : l'identifiant de campagne
     * est public (il s'affiche dans l'écran des campagnes), mais les fiches
     * qu'elle contient ne le sont pas.
     */
    @Test
    @DisplayName("La liste par campagne ne divulgue pas les fiches d'autrui")
    void listeParCampagneNeDivulguePasTout() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(authentifie(
                        get("/api/fiches/evaluation/{id}", campagneSiege.getId()), employeAlice))
                .andReturn().getResponse();

        if (reponse.getStatus() == 200) {
            var corps = objectMapper.readTree(reponse.getContentAsString());
            for (var fiche : corps) {
                assertThat(fiche.path("employeId").asLong())
                        .as("Une fiche d'autrui apparaît dans la liste par campagne")
                        .isEqualTo(employeAlice.getId());
            }
        } else {
            assertThat(reponse.getStatus()).isEqualTo(403);
        }
    }

    /**
     * Le garde de `/api/fiches/n1/{id}` ne se déclenche que si l'appelant porte
     * le rôle N1. Un employé, qui ne le porte pas, le traverse — et récupère
     * toute l'équipe d'un responsable.
     */
    @Test
    @DisplayName("Un employé ne peut pas lister l'équipe d'un N+1")
    void employeNeListePasUneEquipeDeN1() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/n1/{id}", n1Alice.getId()), employeAlice))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    /**
     * Cloisonnement entre branches : le N+1 du siège n'a aucun lien avec les
     * employés d'agence. Rien dans son rôle ne justifie qu'il lise leurs notes.
     */
    @Test
    @DisplayName("Un N+1 ne lit pas la fiche d'un employé d'une autre branche")
    void n1NeLitPasLaFicheHorsDeSonEquipe() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/{id}", ficheAlice.getId()), n1Bob))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("La note d'Alice n'a pas à être visible du N+1 du siège")
                .isIn(403, 404);
    }

    /** Symétrique côté N+2 : lire l'équipe d'un N+1 d'une autre branche. */
    @Test
    @DisplayName("Un N+2 ne liste pas l'équipe d'un N+1 d'une autre branche")
    void n2NeListePasUneEquipeEtrangere() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/n1/{id}", n1Bob.getId()), n2Alice))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    /** Un N+2 ne consulte pas le portefeuille d'un autre N+2. */
    @Test
    @DisplayName("Un N+2 ne consulte pas le portefeuille d'un autre N+2")
    void n2NeConsultePasUnAutreN2() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/fiches/n2/{id}", n2Alice.getId()), n1Bob))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    /**
     * La suppression d'une fiche est le geste le plus irréversible du domaine :
     * elle efface une évaluation validée par trois personnes.
     */
    @Test
    @DisplayName("Un employé ne peut pas supprimer une fiche")
    void employeNeSupprimePasDeFiche() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        delete("/api/fiches/{id}", ficheAlice.getId()), employeAlice))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
        assertThat(ficheRepository.existsById(ficheAlice.getId())).isTrue();
    }

    /** Suppression en masse : un N+1 ne purge pas l'équipe d'un autre. */
    @Test
    @DisplayName("Un N+1 ne purge pas les fiches d'un autre N+1")
    void n1NePurgePasUneAutreEquipe() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        delete("/api/fiches/n1/{id}", n1Alice.getId()), n1Bob))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
        assertThat(ficheRepository.count()).isEqualTo(2);
    }

    // ═══ Répertoire du personnel ═════════════════════════════════════════════

    /**
     * `GET /api/employes/*` n'exige qu'une authentification. Le DTO ne contient
     * pas de mot de passe, mais il expose l'adresse électronique, le rôle et la
     * hiérarchie — de quoi cartographier l'organigramme et cibler un hameçonnage.
     */
    @Test
    @DisplayName("Un employé ne parcourt pas le répertoire du personnel par identifiant")
    void employeNeParcourtPasLeRepertoire() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/employes/{id}", admin.getId()), employeAlice))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Un employé ne doit pas pouvoir lire la fiche du compte administrateur")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("Un employé ne peut pas lister tout le personnel")
    void employeNeListePasToutLePersonnel() throws Exception {
        assertThat(mockMvc.perform(authentifie(get("/api/employes"), employeAlice))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        assertThat(mockMvc.perform(authentifie(get("/api/employes/role/{role}", "ADMIN"), employeAlice))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    /** Un N+1 ne consulte pas les subordonnés d'un autre N+1. */
    @Test
    @DisplayName("Un N+1 ne consulte pas les subordonnés d'un autre N+1")
    void n1NeConsultePasLesSubordonnesDUnAutre() throws Exception {
        int statut = mockMvc.perform(authentifie(
                        get("/api/employes/sous-n1/{id}", n1Alice.getId()), n1Bob))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    // ═══ Campagnes ═══════════════════════════════════════════════════════════

    /** Créer ou supprimer une campagne relève de l'administration. */
    @Test
    @DisplayName("Un employé ne peut pas créer ni supprimer une campagne")
    void employeNeGerePasLesCampagnes() throws Exception {
        Map<String, Object> campagne = new HashMap<>();
        campagne.put("nomEvaluation", "Campagne pirate");
        campagne.put("dateDebut", java.time.LocalDateTime.now().plusDays(1).toString());
        campagne.put("dateFin", java.time.LocalDateTime.now().plusDays(30).toString());
        campagne.put("typeAffectation", "AGENCE");

        assertThat(mockMvc.perform(authentifie(post("/api/evaluations"), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(campagne)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        assertThat(mockMvc.perform(authentifie(
                        delete("/api/evaluations/{id}", campagneAgence.getId()), employeAlice))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        assertThat(evaluationRepository.count()).isEqualTo(2);
    }

    // ═══ Outillage ═══════════════════════════════════════════════════════════

    /** Corps d'évaluation N+1 valide : sans cela un 400 masquerait le 403 attendu. */
    private String corpsEvaluationN1(Long employeId, Long campagneId) throws Exception {
        List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(campagneId);

        Map<String, Double> reponses = new HashMap<>();
        for (Question q : questions) {
            reponses.put(String.valueOf(q.getId()), 8.0);
        }

        Map<String, Object> requete = new HashMap<>();
        requete.put("employeId", employeId);
        requete.put("evaluationId", campagneId);
        requete.put("reponses", reponses);
        requete.put("commentaire", "Test de contrôle d'accès");

        return objectMapper.writeValueAsString(requete);
    }
}
