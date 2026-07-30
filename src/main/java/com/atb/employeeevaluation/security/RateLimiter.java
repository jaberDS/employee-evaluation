package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fenêtre glissante en mémoire sur les endpoints d'authentification.
 *
 * Mono-instance : derrière plusieurs nœuds, il faudrait un compteur partagé
 * (Redis). En l'état, cela suffit à casser le bourrage d'identifiants et
 * l'énumération de matricules, qui sont les attaques réalistes ici.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> tentatives = new ConcurrentHashMap<>();

    /**
     * @param cle        identifiant du compteur (préfixe + matricule ou IP)
     * @param maxParFenetre nombre de tentatives tolérées
     * @param fenetre    largeur de la fenêtre glissante
     */
    public void verifier(String cle, int maxParFenetre, Duration fenetre) {
        long maintenant = System.currentTimeMillis();
        long seuil = maintenant - fenetre.toMillis();

        Deque<Long> horodatages = tentatives.computeIfAbsent(cle, k -> new ArrayDeque<>());
        synchronized (horodatages) {
            while (!horodatages.isEmpty() && horodatages.peekFirst() < seuil) {
                horodatages.pollFirst();
            }
            if (horodatages.size() >= maxParFenetre) {
                throw new RateLimitExceededException(
                        "Trop de tentatives. Veuillez réessayer dans quelques minutes.");
            }
            horodatages.addLast(maintenant);
        }
    }

    /** Appelé après un succès : on ne pénalise pas un utilisateur légitime. */
    public void reinitialiser(String cle) {
        tentatives.remove(cle);
    }

    /** Purge des compteurs entièrement expirés, déclenchée par le scheduler. */
    public void purger(Duration fenetre) {
        long seuil = System.currentTimeMillis() - fenetre.toMillis();
        tentatives.entrySet().removeIf(entry -> {
            Deque<Long> horodatages = entry.getValue();
            synchronized (horodatages) {
                while (!horodatages.isEmpty() && horodatages.peekFirst() < seuil) {
                    horodatages.pollFirst();
                }
                return horodatages.isEmpty();
            }
        });
    }
}
