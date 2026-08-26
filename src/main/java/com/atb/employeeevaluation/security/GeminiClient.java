package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.exception.AssistantUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client du modèle Gemini (palier gratuit).
 *
 * La clé d'API ne quitte jamais le serveur : le navigateur s'adresse à
 * /api/assistant/ask, et c'est ce composant qui parle à Google. Une clé livrée
 * au front serait lisible dans l'onglet réseau et exploitable par n'importe qui.
 *
 * La réponse est contrainte par un schéma JSON — sans quoi le modèle rédigerait
 * de la prose autour du JSON et l'analyse deviendrait un exercice d'expressions
 * régulières, fragile par nature.
 */
@Slf4j
@Component
public class GeminiClient {

    /**
     * Un seul rattrapage, et seulement pour les pannes qui échouent vite (5xx de
     * Google, candidat vide). Réessayer après un dépassement de délai ferait
     * patienter deux fois plus longtemps pour le même échec.
     */
    private static final int MAX_ESSAIS = 2;

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final List<String> modeles;
    private final int maxTokens;
    private final int budgetReflexion;

    /**
     * @param modeles modèles à essayer dans l'ordre, séparés par des virgules.
     *                Le quota gratuit est compté <em>par modèle</em> : une
     *                relève est donc du volume supplémentaire, pas un simple
     *                filet de sécurité. Voir {@link #generer}.
     */
    public GeminiClient(
            @Value("${assistant.gemini.models:gemini-2.5-flash,gemini-flash-lite-latest,"
                    + "gemini-3.1-flash-lite,gemini-3.5-flash-lite}") String modeles,
            @Value("${assistant.gemini.api-key:}") String apiKey,
            @Value("${assistant.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${assistant.gemini.timeout-ms:30000}") long timeoutMs,
            @Value("${assistant.gemini.max-tokens:2048}") int maxTokens,
            @Value("${assistant.gemini.thinking-budget:-1}") int budgetReflexion) {

        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxTokens = maxTokens;
        this.budgetReflexion = budgetReflexion;

        this.modeles = Arrays.stream((modeles == null ? "" : modeles).split(","))
                .map(String::trim)
                .filter(nom -> !nom.isEmpty())
                .toList();
        if (this.modeles.isEmpty()) {
            throw new IllegalArgumentException(
                    "assistant.gemini.models ne peut pas être vide.");
        }

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Sans clé configurée, l'assistant se retire proprement au lieu d'échouer. */
    public boolean estConfigure() {
        return !apiKey.isBlank();
    }

    /**
     * Envoie la consigne et la question, et rend l'objet JSON produit.
     *
     * @param consigne  contexte et règles — rôle, destinations permises, chiffres
     * @param question  texte saisi ou dicté par l'utilisateur
     * @param schema    forme imposée à la réponse
     */
    public JsonNode generer(String consigne, String question, Map<String, Object> schema) {
        return generer(consigne, question, List.of(), schema);
    }

    /**
     * Variante avec historique de conversation.
     *
     * Les tours précédents sont transmis comme de vrais tours de dialogue, et
     * non recopiés dans la consigne : le modèle distingue ainsi ce qui a été dit
     * de ce qui lui est prescrit. L'historique vient du serveur, jamais du
     * client — un tour « modèle » forgé par le navigateur serait un moyen commode
     * de lui faire oublier ses règles.
     *
     * @param historique paires question/réponse, du plus ancien au plus récent
     */
    public JsonNode generer(String consigne, String question,
                            List<Map.Entry<String, String>> historique,
                            Map<String, Object> schema) {
        if (!estConfigure()) {
            throw new AssistantUnavailableException("L'assistant n'est pas configuré sur ce serveur.");
        }

        List<Map<String, Object>> echanges = new ArrayList<>();
        for (Map.Entry<String, String> tour : historique) {
            echanges.add(Map.of("role", "user",
                    "parts", List.of(Map.of("text", tour.getKey()))));
            echanges.add(Map.of("role", "model",
                    "parts", List.of(Map.of("text", tour.getValue()))));
        }
        echanges.add(Map.of("role", "user",
                "parts", List.of(Map.of("text", question))));

        boolean brideReflexion = budgetReflexion >= 0;
        AssistantUnavailableException derniere = null;

        // Le quota gratuit est compté par projet ET PAR MODÈLE, à la journée :
        // `gemini-flash-latest` désignait un modèle plafonné à 20 requêtes par
        // jour, épuisées en une séance de mise au point. L'assistant restait
        // alors muet jusqu'au lendemain. Descendre la liste, c'est autant de
        // compteurs indépendants — la panne devient l'exception, non la règle.
        for (int rang = 0; rang < modeles.size(); rang++) {
            String modele = modeles.get(rang);

            for (int essai = 1; essai <= MAX_ESSAIS; essai++) {
                try {
                    long depart = System.nanoTime();
                    JsonNode sortie = extraireJson(appeler(
                            modele, corps(consigne, echanges, schema, brideReflexion), brideReflexion));
                    if (rang > 0) {
                        log.info("Réponse obtenue du modèle de relève {}.", modele);
                    }
                    log.debug("Réponse de {} en {} ms", modele, (System.nanoTime() - depart) / 1_000_000);
                    return sortie;

                } catch (EchecRattrapable echec) {
                    derniere = echec.finale();

                    if (echec.reflexionRefusee() && brideReflexion) {
                        // Ce modèle n'accepte pas qu'on borne sa réflexion.
                        // Plutôt que de laisser l'assistant hors service jusqu'à
                        // ce qu'on change la propriété, on repart sans ce
                        // réglage — et on l'abandonne pour toute la requête.
                        log.warn("Le modèle {} refuse thinkingConfig : nouvel essai sans bride.", modele);
                        brideReflexion = false;
                        continue;
                    }
                    if (echec.changerDeModele()) {
                        // Quota du jour épuisé, ou modèle retiré du catalogue :
                        // insister sur celui-ci ne rendrait jamais rien.
                        log.warn("Modèle {} indisponible ({}) ; passage au suivant.",
                                modele, echec.getMessage());
                        break;
                    }
                    if (essai < MAX_ESSAIS) {
                        log.warn("Modèle {}, essai {}/{} : {}",
                                modele, essai, MAX_ESSAIS, echec.getMessage());
                    }
                }
            }
        }
        throw derniere;
    }

    // ═══ Requête ═════════════════════════════════════════════════════════════

    /**
     * Corps de la requête.
     *
     * <p>{@code thinkingConfig} borne la réflexion du modèle — ces jetons se
     * déduisent de {@code maxOutputTokens} comme du temps d'attente. Le réglage
     * n'est <strong>pas</strong> envoyé par défaut, et l'expérience commande
     * cette prudence : {@code gemini-flash-latest} suit le dernier modèle Flash,
     * et celui du moment refuse un budget nul par un
     * « Request contains an invalid argument » qui ne nomme même pas le champ
     * fautif. Un réglage d'optimisation avait ainsi mis tout l'assistant à
     * l'arrêt. La mesure ne le justifiait pas : sans lui, la réponse arrive en
     * deux à sept secondes.
     *
     * <p>{@code assistant.gemini.thinking-budget} le rend disponible pour qui
     * voudra l'essayer : {@code -1} (défaut) n'envoie rien, une valeur positive
     * plafonne la réflexion. Un budget refusé n'immobilise plus rien — voir
     * {@link #appeler}.
     */
    private Map<String, Object> corps(String consigne, List<Map<String, Object>> echanges,
                                      Map<String, Object> schema, boolean brideReflexion) {

        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("response_mime_type", "application/json");
        generation.put("response_schema", schema);
        generation.put("temperature", 0.2);
        generation.put("maxOutputTokens", maxTokens);
        if (brideReflexion) {
            generation.put("thinkingConfig", Map.of("thinkingBudget", budgetReflexion));
        }

        // La consigne passe par `system_instruction` plutôt que d'être collée en
        // tête de la question : le modèle distingue ainsi ce qui vient de
        // l'exploitant de ce qui vient de l'utilisateur, et une question rédigée
        // comme un ordre a nettement moins de prise sur son comportement.
        return Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", consigne))),
                "contents", echanges,
                "generationConfig", generation);
    }

    /**
     * Appel HTTP, traduit en pannes intelligibles.
     *
     * @param modele       modèle interrogé pour cet essai
     * @param brideEnvoyee la requête portait-elle {@code thinkingConfig} ; un
     *                     rejet devient alors rattrapable en le retirant
     */
    private String appeler(String modele, Map<String, Object> corps, boolean brideEnvoyee) {
        try {
            return restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{modele}:generateContent")
                            .queryParam("key", apiKey)
                            .build(modele))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(String.class);

        } catch (RestClientResponseException e) {
            // Le détail part dans les journaux ; l'utilisateur reçoit une phrase
            // intelligible. Le corps de la réponse de Google est la seule chose
            // qui explique un 400 — sans lui, le diagnostic est impossible.
            String corpsErreur = e.getResponseBodyAsString();
            log.error("Gemini a rejeté la requête sur {} ({}) : {}",
                    modele, e.getStatusCode(), corpsErreur);

            int statut = e.getStatusCode().value();
            if (statut == 429) {
                // Quota du jour de CE modèle. Le suivant a le sien : on descend
                // la liste plutôt que de renvoyer l'utilisateur à demain.
                throw EchecRattrapable.modeleEpuise("quota de " + modele,
                        "L'assistant a épuisé son quota du jour. Réessayez demain,"
                                + " ou activez la facturation sur la clé Gemini.");
            }
            if (statut == 404) {
                // « no longer available to new users » : Google retire les
                // modèles anciens sans préavis. Une liste écrite un jour finit
                // par contenir un nom mort ; ce n'est pas une raison d'échouer.
                throw EchecRattrapable.modeleEpuise("modèle " + modele + " retiré",
                        "L'assistant est momentanément indisponible.");
            }
            if (statut == 400 && brideEnvoyee) {
                // Un 400 alors qu'on envoyait un réglage facultatif : c'est le
                // suspect le plus probable, et on le retire pour un second essai.
                // La condition ne peut pas être plus fine — Google refuse
                // `thinkingConfig` par un « Request contains an invalid
                // argument » qui ne nomme pas le champ. Chercher le mot
                // « thinking » dans le message ne trouvait donc rien, et le
                // rattrapage ne se déclenchait jamais.
                throw EchecRattrapable.reflexionRefusee(
                        "L'assistant est momentanément indisponible.");
            }
            if (statut >= 500) {
                // « model is overloaded » : fréquent sur le palier gratuit, et
                // passager. Un second essai aboutit le plus souvent.
                throw EchecRattrapable.transitoire("Gemini " + statut,
                        "L'assistant est momentanément indisponible. Réessayez.");
            }
            throw new AssistantUnavailableException("L'assistant est momentanément indisponible.");

        } catch (RestClientException e) {
            // Un dépassement du délai de lecture se présente sous deux formes
            // selon qu'il survient avant ou pendant la réception du corps :
            // ResourceAccessException dans le premier cas, RestClientException nu
            // dans le second. Les deux disaient autre chose que la vérité — d'où
            // le « réponse invalide » qui n'expliquait rien.
            if (delaiDepasse(e)) {
                log.error("Gemini n'a pas répondu dans le délai imparti : {}", e.getMessage());
                throw new AssistantUnavailableException(
                        "L'assistant a mis trop de temps à répondre. Reformulez plus simplement.");
            }
            if (e instanceof ResourceAccessException) {
                log.error("Gemini injoignable : {}", e.getMessage());
                throw new AssistantUnavailableException(
                        "L'assistant est injoignable. Vérifiez la connexion réseau du serveur.");
            }
            log.error("Échange impossible avec Gemini : {}", e.getMessage(), e);
            throw new AssistantUnavailableException("L'assistant est momentanément indisponible.");
        }
    }

    /** Cause profonde : la borne évite de boucler sur une chaîne cyclique. */
    private boolean delaiDepasse(Throwable e) {
        Throwable cause = e;
        for (int profondeur = 0; cause != null && profondeur < 8; profondeur++) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    // ═══ Lecture de la réponse ═══════════════════════════════════════════════

    /**
     * Extrait l'objet JSON de l'enveloppe `candidates[].content.parts[].text`.
     *
     * Le schéma garantit la forme du contenu, pas le fait qu'il y ait un
     * candidat : une réponse bloquée par les filtres de sécurité arrive avec un
     * tableau vide, et une réponse coupée par le plafond de jetons arrive sans
     * la moindre partie. Chacun de ces cas mérite sa propre explication —
     * confondus, ils envoyaient l'utilisateur chercher au mauvais endroit.
     */
    private JsonNode extraireJson(String reponseBrute) {
        JsonNode racine;
        try {
            racine = mapper.readTree(reponseBrute);
        } catch (Exception e) {
            log.error("Enveloppe illisible de Gemini : {}", e.getMessage());
            throw new AssistantUnavailableException("L'assistant a renvoyé une réponse illisible.");
        }

        String blocage = racine.path("promptFeedback").path("blockReason").asText("");
        if (!blocage.isBlank()) {
            log.warn("Question bloquée par les filtres de Gemini ({})", blocage);
            throw new AssistantUnavailableException(
                    "L'assistant a préféré ne pas traiter cette question. Reformulez-la.");
        }

        JsonNode candidat = racine.path("candidates").path(0);
        String fin = candidat.path("finishReason").asText("");
        JsonNode texte = candidat.path("content").path("parts").path(0).path("text");

        if (texte.isMissingNode() || texte.asText().isBlank()) {
            log.warn("Gemini n'a produit aucun texte (finishReason={}) : {}", fin, reponseBrute);

            if ("MAX_TOKENS".equals(fin)) {
                // Le plafond a été atteint avant le premier caractère utile :
                // tout est parti dans la réflexion du modèle. C'est ce qui rend
                // `max-tokens` généreux (2048) — la réflexion s'y impute aussi.
                throw new AssistantUnavailableException(
                        "La réponse dépasse la longueur permise. Posez une question plus précise.");
            }
            if ("SAFETY".equals(fin) || "PROHIBITED_CONTENT".equals(fin)) {
                throw new AssistantUnavailableException(
                        "L'assistant a préféré ne pas répondre à cette question.");
            }
            throw EchecRattrapable.transitoire("candidat vide",
                    "L'assistant n'a pas pu formuler de réponse. Reformulez votre question.");
        }

        try {
            return mapper.readTree(texte.asText());
        } catch (Exception e) {
            // Contenu tronqué : le schéma impose la forme, pas l'achèvement.
            log.error("JSON incomplet produit par Gemini (finishReason={}) : {}", fin, texte.asText());
            throw EchecRattrapable.transitoire("JSON incomplet",
                    "L'assistant a renvoyé une réponse illisible.");
        }
    }

    // ═══ Pannes rattrapables ═════════════════════════════════════════════════

    /**
     * Panne qu'un second essai peut lever, avec la phrase à servir si le second
     * essai échoue lui aussi. Interne au client : l'appelant ne voit jamais que
     * des {@link AssistantUnavailableException}.
     */
    private static final class EchecRattrapable extends RuntimeException {

        private final transient AssistantUnavailableException finale;
        private final boolean reflexionRefusee;
        private final boolean changerDeModele;

        private EchecRattrapable(String motif, String message,
                                 boolean reflexionRefusee, boolean changerDeModele) {
            super(motif);
            this.finale = new AssistantUnavailableException(message);
            this.reflexionRefusee = reflexionRefusee;
            this.changerDeModele = changerDeModele;
        }

        /** Panne passagère : le même modèle peut répondre au second envoi. */
        static EchecRattrapable transitoire(String motif, String message) {
            return new EchecRattrapable(motif, message, false, false);
        }

        static EchecRattrapable reflexionRefusee(String message) {
            return new EchecRattrapable("thinkingConfig refusé", message, true, false);
        }

        /** Ce modèle ne répondra plus aujourd'hui : seul le suivant peut aider. */
        static EchecRattrapable modeleEpuise(String motif, String message) {
            return new EchecRattrapable(motif, message, false, true);
        }

        AssistantUnavailableException finale() {
            return finale;
        }

        boolean reflexionRefusee() {
            return reflexionRefusee;
        }

        boolean changerDeModele() {
            return changerDeModele;
        }
    }
}
