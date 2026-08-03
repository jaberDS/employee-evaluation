package com.atb.employeeevaluation.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Authentification et jetons.
 *
 * Le JWT est le seul élément qui porte l'identité : chaque requête est
 * authentifiée par lui seul, sans session serveur. Tout ce qui permettrait d'en
 * fabriquer un, d'en réutiliser un périmé, ou d'en détourner l'usage donne un
 * accès complet. Ces tests attaquent la signature, le type, l'expiration et le
 * format.
 *
 * L'attaque « alg:none » est traitée en premier parce qu'elle est la plus
 * dévastatrice et la plus mécanique : une bibliothèque qui l'accepte laisse
 * n'importe qui se déclarer administrateur en une requête.
 */
@DisplayName("Authentification et jetons")
class AuthentificationTest extends SecuriteTestBase {

    /** Point protégé, représentatif : il exige une session valide. */
    private static final String POINT_PROTEGE = "/api/auth/me";

    // ═══ Falsification de signature ══════════════════════════════════════════

    /**
     * « alg: none » : l'attaquant remplace l'algorithme par « aucun » et supprime
     * la signature. Une bibliothèque permissive accepte alors des revendications
     * entièrement choisies par le client.
     */
    @Test
    @DisplayName("Un jeton « alg:none » est refusé")
    void jetonSansAlgorithmeEstRefuse() throws Exception {
        String entete = encoder("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String charge = encoder("{\"sub\":\"ADM100\",\"type\":\"access\",\"exp\":"
                + (System.currentTimeMillis() / 1000 + 3600) + "}");
        String jeton = entete + "." + charge + ".";

        mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + jeton))
                .andExpect(status -> assertThat(status.getResponse().getStatus())
                        .as("Un jeton non signé ne doit jamais authentifier")
                        .isIn(401, 403));
    }

    /**
     * Jeton signé avec une autre clé : la structure est parfaite, seule la
     * signature diffère. C'est le contrôle qui distingue une vérification réelle
     * d'un simple décodage.
     */
    @Test
    @DisplayName("Un jeton signé avec une autre clé est refusé")
    void jetonSigneAvecMauvaiseCleEstRefuse() throws Exception {
        var cleEtrangere = Keys.hmacShaKeyFor(
                "cle-de-l-attaquant-suffisamment-longue-pour-hmac-sha256".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> revendications = new HashMap<>();
        revendications.put("type", "access");

        String jeton = Jwts.builder()
                .setClaims(revendications)
                .setSubject(admin.getMatricule())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(cleEtrangere, SignatureAlgorithm.HS256)
                .compact();

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + jeton))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(401, 403);
    }

    /**
     * Charge utile modifiée après signature : on prend un jeton valide d'employé
     * et on réécrit le sujet en administrateur. La signature ne correspond plus.
     */
    @Test
    @DisplayName("Un jeton dont la charge a été réécrite est refusé")
    void jetonAlteReEstRefuse() throws Exception {
        String legitime = jeton(employeAlice);
        String[] parties = legitime.split("\\.");

        String chargeAlteree = encoder("{\"sub\":\"" + admin.getMatricule()
                + "\",\"type\":\"access\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}");
        String forge = parties[0] + "." + chargeAlteree + "." + parties[2];

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + forge))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Une élévation par réécriture du sujet doit échouer")
                .isIn(401, 403);
    }

    // ═══ Détournement d'usage ════════════════════════════════════════════════

    /**
     * Un jeton de rafraîchissement ne doit pas ouvrir l'API : il a une durée de
     * vie bien plus longue, et il est conçu pour ne circuler que vers /refresh.
     */
    @Test
    @DisplayName("Un jeton de rafraîchissement n'authentifie pas une requête d'API")
    void jetonDeRafraichissementNAuthentifiePas() throws Exception {
        String refresh = jwtUtil.generateRefreshToken(admin.getMatricule());

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + refresh))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(401, 403);
    }

    /**
     * Le jeton d'étape MFA est émis **avant** la vérification du second facteur.
     * S'il authentifiait, il suffirait de connaître le mot de passe pour
     * contourner entièrement la double authentification — la faille la plus
     * grave possible dans ce module.
     */
    @Test
    @DisplayName("Un jeton d'étape MFA ne contourne pas le second facteur")
    void jetonMfaEnAttenteNAuthentifiePas() throws Exception {
        String mfa = jwtUtil.generateMfaPendingToken(admin.getMatricule(), "LOGIN_2FA", false);

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + mfa))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Accepter ce jeton reviendrait à supprimer le second facteur")
                .isIn(401, 403);
    }

    /** Le jeton de réinitialisation n'autorise que la pose d'un mot de passe. */
    @Test
    @DisplayName("Un jeton de réinitialisation n'authentifie pas une requête d'API")
    void jetonDeReinitialisationNAuthentifiePas() throws Exception {
        String reset = jwtUtil.generateResetToken(admin.getMatricule());

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + reset))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(401, 403);
    }

    // ═══ Expiration et cycle de vie ══════════════════════════════════════════

    @Test
    @DisplayName("Un jeton expiré est refusé")
    void jetonExpireEstRefuse() throws Exception {
        var cle = Keys.hmacShaKeyFor(
                "test-secret-key-for-jwt-signing-needs-32-bytes-minimum".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> revendications = new HashMap<>();
        revendications.put("type", "access");

        String expire = Jwts.builder()
                .setClaims(revendications)
                .setSubject(admin.getMatricule())
                .setIssuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .setExpiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(cle, SignatureAlgorithm.HS256)
                .compact();

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + expire))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(401, 403);
    }

    /**
     * Un compte désactivé conserve un jeton techniquement valide jusqu'à son
     * expiration. `CustomUserDetailsService` doit refuser de le charger, sans
     * quoi un départ d'employé ne coupe rien pendant une heure.
     */
    @Test
    @DisplayName("Un compte désactivé perd immédiatement l'accès")
    void compteDesactiveEstCoupe() throws Exception {
        String valide = jeton(employeAlice);

        mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + valide))
                .andExpect(s -> assertThat(s.getResponse().getStatus()).isEqualTo(200));

        employeAlice.setActif(false);
        employeRepository.save(employeAlice);

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + valide))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Un compte désactivé ne doit plus être authentifiable")
                .isIn(401, 403);
    }

    // ═══ Absence et malformation ═════════════════════════════════════════════

    @Test
    @DisplayName("Un point protégé refuse une requête sans jeton")
    void requeteSansJetonEstRefusee() throws Exception {
        for (String point : new String[]{POINT_PROTEGE, "/api/employes", "/api/fiches/1",
                "/api/dashboard/stats", "/api/assistant/status", "/api/activites"}) {
            int statut = mockMvc.perform(get(point)).andReturn().getResponse().getStatus();
            assertThat(statut)
                    .as("%s doit exiger une authentification", point)
                    .isIn(401, 403);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer ",
            "Bearer null",
            "Bearer undefined",
            "Bearer abc.def.ghi",
            "Bearer ....",
            "Basic YWRtaW46YWRtaW4=",
            "Bearer eyJhbGciOiJIUzI1NiJ9",
            "'; DROP TABLE employe; --"
    })
    @DisplayName("Un en-tête d'autorisation malformé ne fait pas céder le serveur")
    void enTeteMalformeEstRefuse(String entete) throws Exception {
        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", entete))
                .andReturn().getResponse().getStatus();

        // Un 500 signalerait une exception non maîtrisée dans le filtre : une
        // voie de déni de service, et une fuite de trace d'exécution.
        assertThat(statut)
                .as("En-tête « %s »", entete)
                .isIn(401, 403);
    }

    // ═══ Politique de mot de passe et énumération ════════════════════════════

    /**
     * Un mot de passe faible accepté annule la politique affichée. Le contrôle
     * porte sur le changement de mot de passe, seul point où l'utilisateur en
     * choisit un.
     */
    @Test
    @DisplayName("Un mot de passe faible est refusé au changement")
    void motDePasseFaibleEstRefuse() throws Exception {
        for (String faible : new String[]{"123456", "password", "abc", "aaaaaaaa", "Passw0rd"}) {
            Map<String, String> corps = new HashMap<>();
            corps.put("currentPassword", MOT_DE_PASSE);
            corps.put("newPassword", faible);

            int statut = mockMvc.perform(authentifie(post("/api/auth/change-password"), employeAlice)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(corps)))
                    .andReturn().getResponse().getStatus();

            assertThat(statut)
                    .as("Le mot de passe « %s » ne respecte pas la politique", faible)
                    .isEqualTo(400);
        }
    }

    /**
     * Le changement de mot de passe exige l'ancien. Sans ce contrôle, un jeton
     * volé permettrait de verrouiller définitivement le compte de sa victime.
     */
    @Test
    @DisplayName("Le changement de mot de passe exige l'ancien")
    void changementExigeLAncienMotDePasse() throws Exception {
        Map<String, String> corps = new HashMap<>();
        corps.put("currentPassword", "MauvaisMotDePasse!1");
        corps.put("newPassword", "NouveauPass!2026");

        int statut = mockMvc.perform(authentifie(post("/api/auth/change-password"), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corps)))
                .andReturn().getResponse().getStatus();

        assertThat(statut).isIn(400, 401, 403);
    }

    /**
     * Un mot de passe changé doit invalider les jetons émis avant : c'est le
     * geste réflexe après une compromission, et il serait vain si les sessions
     * ouvertes survivaient.
     */
    @Test
    @DisplayName("Changer de mot de passe invalide les jetons antérieurs")
    void changementInvalideLesJetonsAnterieurs() throws Exception {
        String ancien = jeton(employeAlice);

        // « iat » est arrondi à la seconde et le filtre tolère 1 s d'écart :
        // sans cette attente, le jeton et le changement partagent la même
        // seconde et le test mesurerait l'horloge plutôt que la règle.
        Thread.sleep(1500);

        Map<String, String> corps = new HashMap<>();
        corps.put("currentPassword", MOT_DE_PASSE);
        corps.put("newPassword", "NouveauPass!2026");

        mockMvc.perform(authentifie(post("/api/auth/change-password"), employeAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corps)))
                .andExpect(s -> assertThat(s.getResponse().getStatus()).isEqualTo(200));

        int statut = mockMvc.perform(get(POINT_PROTEGE).header("Authorization", "Bearer " + ancien))
                .andReturn().getResponse().getStatus();

        assertThat(statut)
                .as("Une session ouverte ne doit pas survivre au changement")
                .isIn(401, 403);
    }

    /**
     * Énumération de comptes : la réponse à un matricule inconnu ne doit pas se
     * distinguer de celle à un mot de passe faux, sans quoi on dresse la liste
     * des matricules valides avant de bourrer les identifiants.
     */
    @Test
    @DisplayName("La connexion ne révèle pas quels matricules existent")
    void connexionNeRevelePasLesMatriculesValides() throws Exception {
        Map<String, String> inconnu = Map.of("matricule", "INEXISTANT999", "motDePasse", MOT_DE_PASSE);
        Map<String, String> connuMauvaisPass = Map.of(
                "matricule", employeAlice.getMatricule(), "motDePasse", "MauvaisPass!1");

        var r1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inconnu)))
                .andReturn().getResponse();

        var r2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connuMauvaisPass)))
                .andReturn().getResponse();

        assertThat(r1.getStatus())
                .as("Matricule inconnu (%d) et mot de passe faux (%d) doivent être indiscernables",
                        r1.getStatus(), r2.getStatus())
                .isEqualTo(r2.getStatus());

        // « timestamp » diffère forcément d'un appel à l'autre : on compare ce qui
        // porte de l'information sur l'existence du compte, c'est-à-dire le code
        // d'erreur et le message.
        var corps1 = objectMapper.readTree(r1.getContentAsString());
        var corps2 = objectMapper.readTree(r2.getContentAsString());

        assertThat(corps1.path("message").asText())
                .as("Le message ne doit pas indiquer que le compte est introuvable")
                .isEqualTo(corps2.path("message").asText());
        assertThat(corps1.path("error").asText()).isEqualTo(corps2.path("error").asText());
    }

    /** Le limiteur casse le bourrage d'identifiants : 10 essais par matricule. */
    @Test
    @DisplayName("Le bourrage d'identifiants est bloqué par le limiteur")
    void bourrageDIdentifiantsEstBloque() throws Exception {
        Map<String, String> corps = Map.of(
                "matricule", employeBob.getMatricule(), "motDePasse", "Mauvais!1");
        String json = objectMapper.writeValueAsString(corps);

        boolean bloque = false;
        for (int i = 0; i < 25 && !bloque; i++) {
            int statut = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andReturn().getResponse().getStatus();
            bloque = statut == 429;
        }

        assertThat(bloque)
                .as("Après 25 tentatives, le limiteur doit avoir répondu 429")
                .isTrue();
    }

    /** Un mot de passe ne doit jamais figurer dans une réponse, même haché. */
    @Test
    @DisplayName("Aucune réponse ne divulgue de mot de passe")
    void aucuneReponseNeDivulgueDeMotDePasse() throws Exception {
        for (String point : new String[]{"/api/employes", "/api/auth/me",
                "/api/employes/" + employeAlice.getId()}) {
            var reponse = mockMvc.perform(authentifie(get(point), admin)).andReturn().getResponse();
            if (reponse.getStatus() != 200) {
                continue;
            }
            String corps = reponse.getContentAsString();
            assertThat(corps)
                    .as("%s ne doit contenir aucune empreinte BCrypt", point)
                    .doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("$2y$");
            assertThat(corps).doesNotContain(MOT_DE_PASSE);
        }
    }

    private String encoder(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
