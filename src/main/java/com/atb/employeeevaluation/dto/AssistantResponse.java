package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse de l'assistant.
 *
 * `chemin` est calculé par le serveur à partir du catalogue de routes, jamais
 * recopié depuis la sortie du modèle : le client peut donc s'y fier sans
 * revalider quoi que ce soit. Il est nul quand aucune navigation n'est proposée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantResponse {

    /** Texte à afficher et à lire à voix haute. */
    private String reponse;

    /** Clé de destination retenue, à titre informatif. Nul si aucune. */
    private String cible;

    /** Chemin Angular validé, prêt à être passé au routeur. Nul si aucun. */
    private String chemin;

    /** Libellé de la destination, pour annoncer la redirection. */
    private String libelle;

    /** Graphique à tracer, chiffré par le serveur. Nul si aucun. */
    private AssistantChartDTO graphique;

    /** Questions de suivi proposées en un clic. Éventuellement vide. */
    private List<String> relances;
}
