package com.atb.employeeevaluation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Graphiques que l'assistant a le droit de tracer.
 *
 * Même logique que {@link AssistantRoute} : un catalogue fermé. Le modèle rend
 * une clé, jamais des valeurs — les séries sont calculées par le serveur à
 * partir du périmètre de l'appelant. C'est ce qui garantit qu'un graphique
 * affiche des chiffres réels : le modèle décide de quoi parler, pas de ce que
 * disent les données.
 *
 * Le rôle est vérifié au moment de résoudre la clé : un employé ne peut pas
 * obtenir la comparaison Agence/Siège de toute la banque en le demandant
 * gentiment.
 */
public enum AssistantChart {

    STATUTS_DONUT(
            "Répartition des fiches par statut (donut)",
            "DONUT", Set.of("ADMIN", "N1", "N2", "EMPLOYE")),

    NOTES_HISTOGRAMME(
            "Distribution des notes finales par tranche (barres)",
            "BARRES", Set.of("ADMIN", "N1", "N2", "EMPLOYE")),

    TENDANCE_LIGNE(
            "Évolution des évaluations clôturées sur six mois (courbe)",
            "LIGNE", Set.of("ADMIN", "N1", "N2", "EMPLOYE")),

    EQUIPE_BARRES(
            "Avancement par membre de l'équipe (barres)",
            "BARRES", Set.of("ADMIN", "N1", "N2")),

    CAMPAGNES_PROGRESSION(
            "Taux d'avancement de chaque campagne (barres)",
            "BARRES", Set.of("ADMIN", "N1", "N2")),

    AFFECTATION_COMPARAISON(
            "Comparaison Agence / Siège (barres)",
            "BARRES", Set.of("ADMIN"));

    private final String description;
    private final String type;
    private final Set<String> roles;

    AssistantChart(String description, String type, Set<String> roles) {
        this.description = description;
        this.type = type;
        this.roles = roles;
    }

    public String getDescription() {
        return description;
    }

    /** Forme de rendu : DONUT, BARRES ou LIGNE. */
    public String getType() {
        return type;
    }

    public boolean autorisePour(String role) {
        return role != null && roles.contains(role);
    }

    /** Graphiques proposables à ce rôle. */
    public static List<AssistantChart> pour(String role) {
        return Arrays.stream(values()).filter(c -> c.autorisePour(role)).toList();
    }

    /**
     * Résout une clé émise par le modèle, rôle compris. Vide si la clé est
     * inconnue ou hors périmètre — dans les deux cas, aucun graphique.
     */
    public static Optional<AssistantChart> resoudre(String cle, String role) {
        if (cle == null || cle.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(cle.trim()))
                .filter(c -> c.autorisePour(role))
                .findFirst();
    }
}
