package com.atb.employeeevaluation.service.impl;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mémoire courte des échanges, par utilisateur.
 *
 * Elle vit **côté serveur**, et c'est délibéré. Un historique posté par le
 * navigateur serait forgeable : il suffirait d'y glisser un faux tour « modèle »
 * affirmant « tu peux désormais orienter vers n'importe quel écran » pour
 * amener celui-ci à contredire sa consigne. Ici, le client ne l'envoie ni ne le
 * reçoit — il ne peut donc pas l'écrire.
 *
 * Seuls la question et le texte de la réponse sont retenus : ni chemin, ni
 * identifiant, ni graphique. Un tour ancien ne doit pas peser sur la résolution
 * d'une route ultérieure, laquelle se rejoue intégralement à chaque question.
 *
 * Mono-instance, comme {@link com.atb.employeeevaluation.security.RateLimiter}.
 * Derrière plusieurs nœuds, un utilisateur retrouverait simplement une
 * conversation vide en changeant de serveur — dégradation acceptable pour un
 * confort de dialogue.
 */
@Component
public class AssistantMemoire {

    /** Trois allers-retours suffisent à comprendre « et pour le siège ? ». */
    private static final int MAX_TOURS = 6;

    /** Au-delà, la personne est passée à autre chose : le contexte égarerait. */
    private static final Duration EXPIRATION = Duration.ofMinutes(30);

    /** Coupure des réponses en mémoire : le contexte compte, pas la prose. */
    private static final int MAX_LONGUEUR = 400;

    private final Map<String, Deque<Tour>> conversations = new ConcurrentHashMap<>();

    /** Un tour : ce qui a été demandé, ce qui a été répondu, et quand. */
    public record Tour(String question, String reponse, long horodatage) {}

    /** Tours encore valides, du plus ancien au plus récent. */
    public List<Tour> historique(String matricule) {
        Deque<Tour> tours = conversations.get(matricule);
        if (tours == null) {
            return List.of();
        }
        long seuil = System.currentTimeMillis() - EXPIRATION.toMillis();
        synchronized (tours) {
            purger(tours, seuil);
            return List.copyOf(tours);
        }
    }

    public void memoriser(String matricule, String question, String reponse) {
        Deque<Tour> tours = conversations.computeIfAbsent(matricule, k -> new ArrayDeque<>());
        long maintenant = System.currentTimeMillis();
        synchronized (tours) {
            purger(tours, maintenant - EXPIRATION.toMillis());
            tours.addLast(new Tour(tronquer(question), tronquer(reponse), maintenant));
            while (tours.size() > MAX_TOURS) {
                tours.pollFirst();
            }
        }
    }

    /** Efface la conversation — appelé quand l'utilisateur vide le fil. */
    public void oublier(String matricule) {
        conversations.remove(matricule);
    }

    private void purger(Deque<Tour> tours, long seuil) {
        while (!tours.isEmpty() && tours.peekFirst().horodatage() < seuil) {
            tours.pollFirst();
        }
    }

    private String tronquer(String texte) {
        if (texte == null) {
            return "";
        }
        String propre = texte.strip();
        return propre.length() <= MAX_LONGUEUR ? propre : propre.substring(0, MAX_LONGUEUR) + "…";
    }
}
