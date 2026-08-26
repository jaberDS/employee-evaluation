package com.atb.employeeevaluation.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Configuration : CORS, en-têtes, divulgation, points d'administration.
 *
 * Ces défauts-là ne se voient pas en lisant le code métier — ils vivent dans la
 * configuration, et se révèlent seulement après mise en ligne, quand le site est
 * appelé depuis un autre domaine que le poste du développeur.
 */
@DisplayName("Configuration de sécurité")
class ConfigurationSecuriteTest extends SecuriteTestBase {

    // ═══ CORS ════════════════════════════════════════════════════════════════

    /**
     * Le contrôle qui compte. `allowCredentials(true)` étant activé, un site
     * autorisé peut lire les réponses en portant les identifiants de la victime.
     * Toute origine acceptée est donc une origine de confiance totale.
     */
    @Test
    @DisplayName("Une origine étrangère n'est pas autorisée par CORS")
    void origineEtrangereEstRefusee() throws Exception {
        for (String origine : new String[]{
                "https://pirate.example",
                // Suffixe et préfixe : les deux erreurs classiques d'une
                // comparaison d'origine faite avec « contient » plutôt qu'« égale ».
                "http://localhost.pirate.example",
                "http://pirate.localhost:4200",
                "http://localhost:4201",
                "null"}) {

            MockHttpServletResponse reponse = mockMvc.perform(options("/api/auth/login")
                            .header("Origin", origine)
                            .header("Access-Control-Request-Method", "POST"))
                    .andReturn().getResponse();

            assertThat(reponse.getHeader("Access-Control-Allow-Origin"))
                    .as("L'origine « %s » ne doit pas être renvoyée comme autorisée", origine)
                    .isNotEqualTo(origine);
        }
    }

    /** Contre-épreuve : l'origine légitime doit fonctionner, sinon le site est mort. */
    @Test
    @DisplayName("L'origine du client Angular est autorisée")
    void origineDuClientEstAutorisee() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn().getResponse();

        assertThat(reponse.getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:4200");
    }

    /**
     * Le joker est incompatible avec l'envoi d'identifiants — et le navigateur le
     * refuserait de toute façon. Le voir ici signalerait une configuration
     * relâchée en cours de route.
     */
    @Test
    @DisplayName("CORS n'utilise jamais le joker avec identifiants")
    void corsNUtilisePasLeJoker() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn().getResponse();

        assertThat(reponse.getHeader("Access-Control-Allow-Origin")).isNotEqualTo("*");

        String credentials = reponse.getHeader("Access-Control-Allow-Credentials");
        if ("true".equals(credentials)) {
            assertThat(reponse.getHeader("Access-Control-Allow-Origin"))
                    .as("Joker et identifiants sont incompatibles")
                    .isNotEqualTo("*");
        }
    }

    // ═══ En-têtes de réponse ═════════════════════════════════════════════════

    /**
     * `nosniff` est l'en-tête qui rend inoffensive une charge XSS renvoyée en
     * JSON : sans lui, un navigateur peut deviner le type et interpréter la
     * réponse comme du HTML. C'est le complément direct des tests XSS.
     */
    @Test
    @DisplayName("Les réponses portent les en-têtes de protection du navigateur")
    void enTetesDeProtectionSontPresents() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(authentifie(get("/api/auth/me"), admin))
                .andReturn().getResponse();

        assertThat(reponse.getHeader("X-Content-Type-Options"))
                .as("Sans nosniff, le navigateur peut requalifier une réponse JSON en HTML")
                .isEqualTo("nosniff");

        assertThat(reponse.getHeader("X-Frame-Options"))
                .as("Sans cet en-tête, l'application s'encadre dans un site pirate")
                .isNotNull();

        assertThat(reponse.getHeader("Cache-Control"))
                .as("Une réponse authentifiée ne doit pas être mise en cache")
                .contains("no-store");
    }

    /**
     * L'API est sans état : le jeton porte l'identité. Un cookie de session
     * ajouterait une seconde voie d'authentification, exposée au CSRF — lequel
     * est justement désactivé.
     */
    @Test
    @DisplayName("Aucun cookie de session n'est émis")
    void aucunCookieDeSession() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "matricule", employeAlice.getMatricule(),
                                "motDePasse", MOT_DE_PASSE))))
                .andReturn().getResponse();

        assertThat(reponse.getCookies())
                .as("CSRF est désactivé : un cookie de session serait exploitable")
                .isEmpty();

        String setCookie = reponse.getHeader("Set-Cookie");
        if (setCookie != null) {
            assertThat(setCookie).doesNotContain("JSESSIONID");
        }
    }

    // ═══ Divulgation d'informations ══════════════════════════════════════════

    /**
     * Un message d'erreur qui recopie l'exception livre des noms de tables, des
     * fragments de requête et des chemins de fichiers. C'est le premier travail
     * d'un attaquant : faire parler les erreurs.
     */
    @Test
    @DisplayName("Aucune réponse d'erreur ne divulgue d'interne du serveur")
    void erreursNeDivulguentRien() throws Exception {
        String[][] sondes = {
                {"GET", "/api/fiches/999999"},
                {"GET", "/api/employes/999999"},
                {"GET", "/api/evaluations/999999"},
                {"GET", "/api/fiches/abc"},
                {"GET", "/api/inexistant"},
        };

        for (String[] sonde : sondes) {
            MockHttpServletResponse reponse = mockMvc.perform(
                            authentifie(get(sonde[1]), admin))
                    .andReturn().getResponse();

            String corps = reponse.getContentAsString();
            assertThat(corps)
                    .as("%s %s", sonde[0], sonde[1])
                    .doesNotContain("com.atb.employeeevaluation")
                    .doesNotContain("org.hibernate")
                    .doesNotContain("org.springframework")
                    .doesNotContain("java.lang")
                    .doesNotContain("Caused by")
                    .doesNotContain("at com.")
                    .doesNotContain("C:\\")
                    .doesNotContain("select ")
                    .doesNotContain("insert into");
        }
    }

    /**
     * Un jeton illisible ne doit pas provoquer d'explication détaillée : dire
     * *pourquoi* il est refusé (signature, algorithme, expiration) guide celui
     * qui cherche à en fabriquer un.
     */
    @Test
    @DisplayName("Un jeton refusé n'explique pas pourquoi")
    void jetonRefuseNExpliquePas() throws Exception {
        MockHttpServletResponse reponse = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJBRE0xMDAifQ."))
                .andReturn().getResponse();

        String corps = reponse.getContentAsString();
        assertThat(corps)
                .doesNotContain("signature")
                .doesNotContain("Signature")
                .doesNotContain("HS256")
                .doesNotContain("io.jsonwebtoken");
    }

    /** Le secret de signature ne doit jamais franchir la frontière HTTP. */
    @Test
    @DisplayName("Aucune réponse ne contient le secret de signature")
    void secretDeSignatureNeSortPas() throws Exception {
        for (String point : new String[]{"/api/auth/me", "/api/employes", "/api/dashboard/stats"}) {
            MockHttpServletResponse reponse = mockMvc.perform(authentifie(get(point), admin))
                    .andReturn().getResponse();

            assertThat(reponse.getContentAsString())
                    .as(point)
                    .doesNotContain("test-secret-key-for-jwt-signing")
                    .doesNotContain("jwt.secret");
        }
    }

    // ═══ Points d'administration ═════════════════════════════════════════════

    /**
     * Seul `/actuator/health` est public. `env`, `configprops` et `beans`
     * exposeraient la configuration entière — secrets compris.
     */
    @Test
    @DisplayName("Seul le point de santé de l'actuateur est public")
    void actuateurEstFerme() throws Exception {
        for (String point : new String[]{"/actuator/env", "/actuator/configprops",
                "/actuator/beans", "/actuator/mappings", "/actuator/heapdump", "/actuator"}) {

            int statut = mockMvc.perform(get(point)).andReturn().getResponse().getStatus();

            assertThat(statut)
                    .as("%s ne doit pas répondre 200 sans authentification", point)
                    .isNotEqualTo(200);
        }
    }

    /** La console H2 sert au développement ; en ligne, c'est un shell SQL ouvert. */
    @Test
    @DisplayName("La console H2 n'est pas exposée")
    void consoleH2NEstPasExposee() throws Exception {
        for (String point : new String[]{"/h2-console", "/h2-console/login.jsp", "/h2"}) {
            int statut = mockMvc.perform(get(point)).andReturn().getResponse().getStatus();
            assertThat(statut)
                    .as("%s ne doit pas être accessible", point)
                    .isNotEqualTo(200);
        }
    }

    // ═══ Limitation de débit ═════════════════════════════════════════════════

    /**
     * `X-Forwarded-For` est écrit par le client. Le croire sans condition rendait
     * le compteur par IP inutile : une valeur différente à chaque essai ouvrait un
     * compteur neuf, et l'on balayait les matricules sans jamais être ralenti.
     *
     * L'en-tête n'étant plus retenu qu'en provenance d'un relais déclaré, la
     * rotation ne change rien — le compteur finit par se déclencher.
     */
    @Test
    @DisplayName("Changer X-Forwarded-For ne remet pas le compteur à zéro")
    void enTeteForwardedNeContournePasLeLimiteur() throws Exception {
        boolean bloque = false;

        for (int i = 0; i < 60 && !bloque; i++) {
            String json = objectMapper.writeValueAsString(Map.of(
                    // Un matricule différent à chaque essai : le compteur par
                    // matricule ne peut donc pas être celui qui se déclenche.
                    "matricule", "INCONNU" + i,
                    "motDePasse", "Mauvais!1"));

            int statut = mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "10.0.0." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andReturn().getResponse().getStatus();

            bloque = statut == 429;
        }

        assertThat(bloque)
                .as("Le compteur par IP doit résister à la rotation de l'en-tête")
                .isTrue();
    }
}
