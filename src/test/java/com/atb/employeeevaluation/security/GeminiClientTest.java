package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.exception.AssistantUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Comportement du client Gemini face à un service qui répond mal.
 *
 * Ces cas-là ne se voyaient pas : lents, intermittents, dépendants de la
 * question posée. Sur une question suggérée le modèle tranchait vite et tout
 * fonctionnait ; sur une question inattendue il délibérait, dépassait le délai
 * de lecture, et l'utilisateur recevait « L'assistant a renvoyé une réponse
 * invalide » — une phrase qui recouvrait quatre pannes distinctes et n'en
 * désignait aucune.
 *
 * Un vrai serveur HTTP local sert de doublure : la panne doit être reproduite au
 * niveau de la socket, puisque c'est justement à ce niveau que le défaut se
 * manifestait. Aucun appel n'est fait à Google, aucun quota n'est consommé.
 */
@DisplayName("Client Gemini — pannes et messages")
class GeminiClientTest {

    private static final String CLE = "cle-de-test-a-ne-jamais-divulguer";
    private static final Map<String, Object> SCHEMA = Map.of("type", "OBJECT");

    private final ObjectMapper mapper = new ObjectMapper();

    /** Réponses à servir, dans l'ordre ; une par requête reçue. */
    private final BlockingQueue<Reponse> reponses = new LinkedBlockingQueue<>();
    private final List<String> corpsRecus = new CopyOnWriteArrayList<>();
    private final List<String> urisRecues = new CopyOnWriteArrayList<>();

    private HttpServer serveur;
    private int port;

    /** @param attenteAvantCorps millisecondes d'attente après l'envoi des en-têtes */
    private record Reponse(int statut, String corps, long attenteAvantCorps) {

        static Reponse de(int statut, String corps) {
            return new Reponse(statut, corps, 0);
        }
    }

    @BeforeEach
    void demarrerLaDoublure() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serveur.createContext("/v1beta", echange -> {
            corpsRecus.add(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            urisRecues.add(echange.getRequestURI().toString());

            Reponse reponse = reponses.poll();
            if (reponse == null) {
                reponse = Reponse.de(500, "{\"error\":\"aucune réponse prévue\"}");
            }
            byte[] corps = reponse.corps().getBytes(StandardCharsets.UTF_8);

            echange.getResponseHeaders().add("Content-Type", "application/json");
            // Les en-têtes partent ici ; le corps, lui, attend. C'est exactement
            // la situation qui produisait un RestClientException nu plutôt qu'un
            // ResourceAccessException — et donc le mauvais message.
            echange.sendResponseHeaders(reponse.statut(), corps.length);
            if (reponse.attenteAvantCorps() > 0) {
                try {
                    Thread.sleep(reponse.attenteAvantCorps());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try (OutputStream sortie = echange.getResponseBody()) {
                sortie.write(corps);
            }
        });
        serveur.setExecutor(Executors.newCachedThreadPool());
        serveur.start();
        port = serveur.getAddress().getPort();
    }

    @AfterEach
    void arreterLaDoublure() {
        serveur.stop(0);
    }

    private GeminiClient client(long delaiMs, int budgetReflexion) {
        return client("modele-unique", delaiMs, budgetReflexion);
    }

    private GeminiClient client(String modeles, long delaiMs, int budgetReflexion) {
        return new GeminiClient(modeles, CLE,
                "http://localhost:" + port + "/v1beta", delaiMs, 2048, budgetReflexion);
    }

    /** Réglages de production : aucune bride de réflexion. */
    private GeminiClient client() {
        return client(5000, -1);
    }

    // ═══ Le défaut signalé ═══════════════════════════════════════════════════

    /**
     * Le cas rapporté. Le message ne doit plus parler de réponse « invalide » :
     * la réponse n'avait rien d'invalide, elle n'était pas encore arrivée.
     */
    @Test
    @DisplayName("Un dépassement de délai le dit, au lieu d'accuser la réponse")
    void delaiDepasseEstNomme() {
        reponses.add(new Reponse(200, enveloppe("Bonjour"), 1500));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client(300, 0).generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage())
                .as("La phrase doit désigner la cause réelle")
                .contains("trop de temps")
                .doesNotContain("invalide");
    }

    /**
     * Une régression payée cher. Brider la réflexion du modèle paraissait un
     * gain — ces jetons se déduisent du délai *et* du plafond. Mais
     * `gemini-flash-latest` suit le dernier modèle Flash, et celui du moment
     * refuse le champ : l'assistant entier tombait, y compris les questions
     * suggérées qui fonctionnaient auparavant. Le réglage n'est donc plus
     * envoyé sans qu'on le demande explicitement.
     */
    @Test
    @DisplayName("Aucune bride de réflexion n'est envoyée par défaut")
    void aucuneBrideParDefaut() {
        reponses.add(Reponse.de(200, enveloppe("Trois fiches attendent votre saisie.")));

        client().generer("consigne", "question", SCHEMA);

        assertThat(corpsRecus).hasSize(1);
        assertThat(corpsRecus.get(0))
                .as("Un champ facultatif que le modèle peut refuser ne part pas de lui-même")
                .doesNotContain("thinkingConfig");
    }

    /** Contre-épreuve : un budget explicite est bien transmis. */
    @Test
    @DisplayName("Un budget explicite est transmis au modèle")
    void budgetExpliciteEstTransmis() {
        reponses.add(Reponse.de(200, enveloppe("Réponse")));

        client(5000, 128).generer("consigne", "question", SCHEMA);

        assertThat(corpsRecus.get(0))
                .contains("thinkingConfig")
                .contains("\"thinkingBudget\":128");
    }

    // ═══ Réponses incomplètes ════════════════════════════════════════════════

    /**
     * Le modèle réfléchit par défaut et ces jetons s'imputent sur le plafond :
     * s'il est trop bas, la réponse revient sans la moindre partie. Le cas doit
     * être nommé, sans quoi un plafond mal réglé ressemble à une panne.
     */
    @Test
    @DisplayName("Un plafond de jetons atteint est annoncé comme tel")
    void plafondDeJetonsEstAnnonce() {
        String tronquee = "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\","
                + "\"content\":{\"role\":\"model\"}}]}";
        reponses.add(Reponse.de(200, tronquee));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client().generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage())
                .contains("longueur")
                .doesNotContain("invalide");
    }

    /** Une question écartée par les filtres n'est pas une panne du serveur. */
    @Test
    @DisplayName("Un blocage par les filtres est distingué d'une panne")
    void blocageParLesFiltresEstDistingue() {
        reponses.add(Reponse.de(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client().generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage()).contains("préféré ne pas traiter");
    }

    // ═══ Rattrapage ══════════════════════════════════════════════════════════

    /**
     * « The model is overloaded » est courant sur le palier gratuit et ne dure
     * pas. Un second envoi coûte une seconde ; laisser passer l'échec coûtait la
     * question de l'utilisateur.
     */
    @Test
    @DisplayName("Une panne passagère de Google est rattrapée")
    void pannePassagereEstRattrapee() {
        reponses.add(Reponse.de(503, "{\"error\":{\"message\":\"model is overloaded\"}}"));
        reponses.add(Reponse.de(200, enveloppe("Deux campagnes sont ouvertes.")));

        JsonNode sortie = client().generer("consigne", "question", SCHEMA);

        assertThat(sortie.path("reponse").asText()).isEqualTo("Deux campagnes sont ouvertes.");
        assertThat(corpsRecus).as("Un seul rattrapage").hasSize(2);
    }

    /** Deux pannes de suite : on cesse, sans laisser l'utilisateur sans réponse. */
    @Test
    @DisplayName("Le rattrapage ne se répète pas indéfiniment")
    void rattrapageNeBoucePas() {
        reponses.add(Reponse.de(503, "{\"error\":\"indisponible\"}"));
        reponses.add(Reponse.de(503, "{\"error\":\"indisponible\"}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client().generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(corpsRecus).hasSize(2);
    }

    /**
     * Tous les modèles n'acceptent pas qu'on borne leur réflexion, et un
     * exploitant peut régler ce budget en connaissance de cause. Le refus ne
     * doit pas immobiliser l'assistant jusqu'à ce qu'on modifie la propriété.
     *
     * <p>Le corps reproduit ici est celui que Google renvoie réellement :
     * <em>opaque</em>. Il ne nomme pas le champ fautif, pas même le mot
     * « thinking ». Une première version cherchait ce mot dans le message pour
     * décider du rattrapage — elle ne s'est jamais déclenchée, et l'assistant
     * est resté hors service. D'où la règle actuelle, plus large : tout 400
     * reçu alors qu'on envoyait ce champ facultatif justifie un essai sans lui.
     */
    @Test
    @DisplayName("Une bride refusée est abandonnée, même sur un refus muet")
    void brideRefuseeEstAbandonnee() {
        reponses.add(Reponse.de(400, "{\"error\":{\"code\":400,\"message\":"
                + "\"Request contains an invalid argument.\","
                + "\"status\":\"INVALID_ARGUMENT\"}}"));
        reponses.add(Reponse.de(200, enveloppe("Voici la répartition.")));

        JsonNode sortie = client(5000, 0).generer("consigne", "question", SCHEMA);

        assertThat(sortie.path("reponse").asText()).isEqualTo("Voici la répartition.");
        assertThat(corpsRecus).hasSize(2);
        assertThat(corpsRecus.get(0)).contains("thinkingConfig");
        assertThat(corpsRecus.get(1))
                .as("Le second envoi abandonne le réglage refusé")
                .doesNotContain("thinkingConfig");
    }

    /**
     * Contre-épreuve : sans bride envoyée, un 400 est définitif. Le rattrapage
     * ne doit pas s'étendre à tous les 400 — insister sur une requête que
     * Google juge malformée ne fait que doubler l'attente.
     */
    @Test
    @DisplayName("Sans bride envoyée, un 400 n'est pas réessayé")
    void quatreCentSansBrideEstDefinitif() {
        reponses.add(Reponse.de(400, "{\"error\":{\"code\":400,\"message\":"
                + "\"Request contains an invalid argument.\"}}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client().generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(corpsRecus).as("Rien à retirer, donc rien à réessayer").hasSize(1);
    }

    /** Le quota est une limite, pas une panne : le message doit le dire. */
    @Test
    @DisplayName("Le quota épuisé garde son message propre")
    void quotaEpuiseGardeSonMessage() {
        reponses.add(Reponse.de(429, "{\"error\":{\"message\":\"quota exceeded\"}}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client().generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage()).contains("quota");
        assertThat(corpsRecus)
                .as("Un seul modèle configuré : insister épuiserait davantage le quota")
                .hasSize(1);
    }

    // ═══ Relève entre modèles ════════════════════════════════════════════════

    /**
     * Le défaut le plus coûteux du lot, et le moins visible : le quota gratuit
     * de Google se compte <em>par modèle</em> et <em>par jour</em>. Le modèle
     * retenu au départ n'en accordait que vingt. Vingt questions et l'assistant
     * se taisait jusqu'au lendemain — sans rien qui distingue cette panne d'une
     * autre.
     *
     * <p>Chaque modèle de la liste ayant son propre compteur, descendre la liste
     * additionne les quotas au lieu de les partager.
     */
    @Test
    @DisplayName("Un quota épuisé passe au modèle suivant")
    void quotaEpuisePasseAuModeleSuivant() {
        reponses.add(Reponse.de(429, "{\"error\":{\"message\":\"quota exceeded\"}}"));
        reponses.add(Reponse.de(200, enveloppe("Sept fiches sont en attente.")));

        JsonNode sortie = client("modele-a,modele-b", 5000, -1)
                .generer("consigne", "question", SCHEMA);

        assertThat(sortie.path("reponse").asText()).isEqualTo("Sept fiches sont en attente.");
        assertThat(urisRecues).hasSize(2);
        assertThat(urisRecues.get(0)).contains("modele-a");
        assertThat(urisRecues.get(1))
                .as("La question aboutit sur la relève, sans que l'utilisateur le sache")
                .contains("modele-b");
    }

    /**
     * Google retire les modèles anciens sans préavis — « no longer available to
     * new users », en 404. Une liste écrite un jour finit par contenir un nom
     * mort ; ce n'est pas une raison de refuser la question.
     */
    @Test
    @DisplayName("Un modèle retiré du catalogue est sauté")
    void modeleRetireEstSaute() {
        reponses.add(Reponse.de(404, "{\"error\":{\"message\":\"no longer available\"}}"));
        reponses.add(Reponse.de(200, enveloppe("Deux campagnes sont ouvertes.")));

        JsonNode sortie = client("modele-mort,modele-vivant", 5000, -1)
                .generer("consigne", "question", SCHEMA);

        assertThat(sortie.path("reponse").asText()).isEqualTo("Deux campagnes sont ouvertes.");
        assertThat(urisRecues.get(1)).contains("modele-vivant");
    }

    /**
     * La relève ne doit pas devenir une insistance : un modèle épuisé n'est
     * essayé qu'une fois. Sans cela, quatre modèles feraient huit appels et
     * quadrupleraient l'attente avant d'avouer l'échec.
     */
    @Test
    @DisplayName("Un modèle épuisé n'est pas réessayé sur lui-même")
    void modeleEpuiseNestPasReessaye() {
        reponses.add(Reponse.de(429, "{\"error\":{\"message\":\"quota exceeded\"}}"));
        reponses.add(Reponse.de(429, "{\"error\":{\"message\":\"quota exceeded\"}}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client("modele-a,modele-b", 5000, -1)
                        .generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage()).contains("quota");
        assertThat(corpsRecus).as("Un appel par modèle, pas deux").hasSize(2);
    }

    /**
     * Toute la liste épuisée : le message doit désigner la cause réelle et la
     * sortie. « Momentanément indisponible » enverrait l'exploitant chercher une
     * panne réseau là où il n'y a qu'un plafond de facturation.
     */
    @Test
    @DisplayName("Toute la liste épuisée, le message nomme le quota et l'issue")
    void listeEntierementEpuiseeExpliqueLIssue() {
        reponses.add(Reponse.de(429, "{\"error\":{\"message\":\"quota exceeded\"}}"));
        reponses.add(Reponse.de(429, "{\"error\":{\"message\":\"quota exceeded\"}}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client("modele-a,modele-b", 5000, -1)
                        .generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage())
                .contains("quota")
                .contains("facturation");
    }

    /** Une liste vide est une erreur de configuration, pas un cas d'exécution. */
    @Test
    @DisplayName("Une liste de modèles vide est refusée au démarrage")
    void listeDeModelesVideEstRefusee() {
        assertThatThrownBy(() -> client(" , ", 5000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ═══ Secret ══════════════════════════════════════════════════════════════

    /**
     * La clé voyage vers Google, jamais vers l'utilisateur. Un message d'erreur
     * qui recopie le corps renvoyé par Google la ferait remonter jusqu'au
     * navigateur — Google renvoie volontiers la requête fautive dans ses 400.
     */
    @Test
    @DisplayName("La clé part vers Google et ne revient jamais à l'utilisateur")
    void cleNeRemontePasALUtilisateur() {
        reponses.add(Reponse.de(400, "{\"error\":{\"message\":\"API key not valid: " + CLE + "\"}}"));

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> client().generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage())
                .as("Le corps renvoyé par Google ne doit pas être recopié tel quel")
                .doesNotContain(CLE);

        assertThat(urisRecues.get(0))
                .as("Contre-épreuve : elle est bien transmise à Google")
                .contains("key=" + CLE);
    }

    /** Sans clé, l'assistant se retire au lieu d'appeler dans le vide. */
    @Test
    @DisplayName("Sans clé configurée, aucun appel n'est tenté")
    void sansCleAucunAppel() {
        GeminiClient sansCle = new GeminiClient("modele-unique", "  ",
                "http://localhost:" + port + "/v1beta", 5000, 2048, -1);

        assertThat(sansCle.estConfigure()).isFalse();

        AssistantUnavailableException erreur = catchThrowableOfType(
                () -> sansCle.generer("consigne", "question", SCHEMA),
                AssistantUnavailableException.class);

        assertThat(erreur).isNotNull();
        assertThat(erreur.getMessage()).contains("n'est pas configuré");
        assertThat(corpsRecus).isEmpty();
    }

    // ═══ Historique ══════════════════════════════════════════════════════════

    /**
     * L'historique doit arriver comme de vrais tours de dialogue. Recopié dans
     * la consigne, il se confondrait avec les règles — et une phrase de
     * l'utilisateur y prendrait le rang d'une instruction de l'exploitant.
     */
    @Test
    @DisplayName("L'historique est transmis comme dialogue, pas comme consigne")
    void historiqueEstTransmisCommeDialogue() {
        reponses.add(Reponse.de(200, enveloppe("Deux sont clôturées.")));

        client().generer("consigne du serveur", "et clôturées ?",
                List.of(Map.entry("combien en attente ?", "Sept sont en attente.")),
                SCHEMA);

        String envoi = corpsRecus.get(0);
        assertThat(envoi).contains("combien en attente ?").contains("Sept sont en attente.");
        assertThat(envoi).contains("\"role\":\"model\"");
        assertThat(envoi)
                .as("La consigne reste dans system_instruction")
                .contains("system_instruction");
    }

    // ═══ Fabrique d'enveloppes ═══════════════════════════════════════════════

    /** Enveloppe Gemini complète, le texte du candidat étant lui-même du JSON. */
    private String enveloppe(String reponse) {
        try {
            ObjectNode contenu = mapper.createObjectNode();
            contenu.put("reponse", reponse);
            contenu.put("cible", "");
            contenu.put("parametre", "");
            contenu.put("graphique", "");
            contenu.set("relances", mapper.createArrayNode());

            ObjectNode partie = mapper.createObjectNode();
            partie.put("text", mapper.writeValueAsString(contenu));

            ObjectNode contenuCandidat = mapper.createObjectNode();
            contenuCandidat.put("role", "model");
            contenuCandidat.set("parts", mapper.createArrayNode().add(partie));

            ObjectNode candidat = mapper.createObjectNode();
            candidat.set("content", contenuCandidat);
            candidat.put("finishReason", "STOP");

            ObjectNode racine = mapper.createObjectNode();
            racine.set("candidates", mapper.createArrayNode().add(candidat));
            return mapper.writeValueAsString(racine);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
