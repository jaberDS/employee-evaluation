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

import java.time.Duration;
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

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String modele;

    public GeminiClient(
            @Value("${assistant.gemini.api-key:}") String apiKey,
            @Value("${assistant.gemini.model:gemini-flash-latest}") String modele,
            @Value("${assistant.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${assistant.gemini.timeout-ms:20000}") long timeoutMs) {

        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modele = modele;

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
        if (!estConfigure()) {
            throw new AssistantUnavailableException("L'assistant n'est pas configuré sur ce serveur.");
        }

        // La consigne passe par `system_instruction` plutôt que d'être collée en
        // tête de la question : le modèle distingue ainsi ce qui vient de
        // l'exploitant de ce qui vient de l'utilisateur, et une question rédigée
        // comme un ordre a nettement moins de prise sur son comportement.
        Map<String, Object> corps = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", consigne))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", question)))),
                "generationConfig", Map.of(
                        "response_mime_type", "application/json",
                        "response_schema", schema,
                        "temperature", 0.2,
                        "maxOutputTokens", 800));

        try {
            String reponse = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{modele}:generateContent")
                            .queryParam("key", apiKey)
                            .build(modele))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(String.class);

            return extraireJson(reponse);

        } catch (RestClientResponseException e) {
            // 4xx : clé invalide, quota épuisé, requête malformée. Le détail part
            // dans les journaux ; l'utilisateur reçoit une phrase intelligible.
            log.error("Gemini a rejeté la requête ({}) : {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new AssistantUnavailableException(
                        "L'assistant a atteint son quota. Réessayez dans quelques instants.");
            }
            throw new AssistantUnavailableException("L'assistant est momentanément indisponible.");

        } catch (ResourceAccessException e) {
            log.error("Gemini injoignable : {}", e.getMessage());
            throw new AssistantUnavailableException(
                    "L'assistant est injoignable. Vérifiez la connexion réseau du serveur.");

        } catch (RestClientException e) {
            // Corps illisible ou type de contenu inattendu : sans ce filet, une
            // réponse malformée remonterait en 500 « erreur inattendue ».
            log.error("Réponse illisible de Gemini : {}", e.getMessage());
            throw new AssistantUnavailableException("L'assistant a renvoyé une réponse invalide.");
        }
    }

    /**
     * Extrait l'objet JSON de l'enveloppe `candidates[].content.parts[].text`.
     *
     * Le schéma garantit la forme du contenu, pas le fait qu'il y ait un
     * candidat : une réponse bloquée par les filtres de sécurité arrive avec un
     * tableau vide.
     */
    private JsonNode extraireJson(String reponseBrute) {
        try {
            JsonNode racine = mapper.readTree(reponseBrute);
            JsonNode texte = racine.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");

            if (texte.isMissingNode() || texte.asText().isBlank()) {
                log.warn("Gemini n'a produit aucun texte exploitable : {}", reponseBrute);
                throw new AssistantUnavailableException(
                        "L'assistant n'a pas pu formuler de réponse. Reformulez votre question.");
            }
            return mapper.readTree(texte.asText());

        } catch (AssistantUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Analyse impossible de la réponse Gemini : {}", e.getMessage());
            throw new AssistantUnavailableException("L'assistant a renvoyé une réponse illisible.");
        }
    }
}
