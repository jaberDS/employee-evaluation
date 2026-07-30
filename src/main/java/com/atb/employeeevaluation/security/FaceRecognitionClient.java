package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.dto.FaceFrameDTO;
import com.atb.employeeevaluation.exception.FaceVerificationException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
 * Client du microservice Python de reconnaissance faciale.
 *
 * Le service ne rend que des embeddings et des verdicts de vivacité : la
 * décision d'identité (comparaison au gabarit, seuil) reste dans FaceServiceImpl,
 * avec le reste de la logique d'authentification.
 */
@Slf4j
@Component
public class FaceRecognitionClient {

    private final RestClient restClient;

    public FaceRecognitionClient(@Value("${face.service.url:http://127.0.0.1:8000}") String baseUrl,
                                 @Value("${face.service.timeout-ms:20000}") long timeoutMs) {
        // Le premier appel après démarrage peut être lent (chargement ONNX) ;
        // au-delà de ce délai, mieux vaut échouer proprement qu'occuper un thread.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Embedding d'une image unique. Utilisé pour un contrôle de qualité rapide. */
    public EmbedResult embed(String imageBase64) {
        return appeler("/embed", Map.of("image", imageBase64), EmbedResult.class);
    }

    /**
     * Vérifie qu'une séquence exécute bien les consignes et provient d'une même
     * personne vivante. Renvoie l'embedding moyen des trames exploitables.
     */
    public LivenessResult verifyLiveness(List<FaceFrameDTO> frames) {
        List<Map<String, String>> corps = frames.stream()
                .map(f -> Map.of("action", f.getAction(), "image", f.getImage()))
                .toList();
        return appeler("/verify-liveness", Map.of("frames", corps), LivenessResult.class);
    }

    /** Le service répond-il ? Sert à afficher un message utile plutôt qu'un 500. */
    public boolean isUp() {
        try {
            restClient.get().uri("/health").retrieve().body(String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T appeler(String chemin, Object corps, Class<T> type) {
        try {
            return restClient.post()
                    .uri(chemin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(type);

        } catch (RestClientResponseException e) {
            // 4xx du service Python : le problème vient de l'image, pas de l'infra.
            String detail = extraireDetail(e.getResponseBodyAsString());
            log.info("Service facial a rejeté {} : {}", chemin, detail);
            throw new FaceVerificationException(detail);

        } catch (ResourceAccessException e) {
            log.error("Service facial injoignable sur {} : {}", chemin, e.getMessage());
            throw new FaceServiceUnavailableException(
                    "Le service de reconnaissance faciale est indisponible. Réessayez plus tard.");

        } catch (RestClientException e) {
            // Réponse illisible : corps vide, type de contenu absent, JSON invalide.
            // Arrive quand le service Python plante en cours de sérialisation — un
            // NaN suffit — et laisse uvicorn répondre sans en-tête exploitable.
            // Sans ce filet l'exception remonte en 500 « erreur inattendue », avec
            // le nom des classes Java à l'écran.
            log.error("Réponse illisible du service facial sur {} : {}", chemin, e.getMessage());
            throw new FaceServiceUnavailableException(
                    "Le service de reconnaissance faciale a renvoyé une réponse invalide. Réessayez.");
        }
    }

    /** FastAPI renvoie {"detail": "..."} ; on évite de propager du JSON brut à l'écran. */
    private String extraireDetail(String corps) {
        if (corps == null || corps.isBlank()) {
            return "La vérification faciale a échoué";
        }
        int debut = corps.indexOf("\"detail\"");
        if (debut < 0) {
            return "La vérification faciale a échoué";
        }
        int guillemetOuvrant = corps.indexOf('"', corps.indexOf(':', debut) + 1);
        int guillemetFermant = guillemetOuvrant < 0 ? -1 : corps.indexOf('"', guillemetOuvrant + 1);
        if (guillemetOuvrant < 0 || guillemetFermant < 0) {
            return "La vérification faciale a échoué";
        }
        return corps.substring(guillemetOuvrant + 1, guillemetFermant);
    }

    // ─── Réponses du service ──────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedResult {
        private List<Double> embedding;
        private double quality;
        private double detScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessResult {
        private boolean live;
        private boolean identityConsistent;
        private List<StepVerdict> steps;
        private List<Double> embedding;
        private double quality;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepVerdict {
        private String action;
        private boolean passed;
        private String reason;
    }

    /** Panne d'infrastructure, à distinguer d'un échec de vérification (503 vs 401). */
    public static class FaceServiceUnavailableException extends RuntimeException {
        public FaceServiceUnavailableException(String message) {
            super(message);
        }
    }
}
