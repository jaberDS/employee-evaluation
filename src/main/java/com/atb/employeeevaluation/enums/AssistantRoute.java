package com.atb.employeeevaluation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Destinations que l'assistant a le droit de proposer.
 *
 * C'est un catalogue fermé, et c'est là tout l'intérêt : le modèle ne renvoie
 * jamais une URL mais une clé de cet enum. Une URL libre ouvrirait une
 * redirection arbitraire — il suffirait d'une injection dans la question de
 * l'utilisateur pour envoyer celui-ci n'importe où. Ici, une clé inconnue ne
 * correspond à rien et l'action est simplement abandonnée.
 *
 * Les rôles répliquent ceux de app-routing.module.ts. Cette liste ne remplace
 * pas les gardes de sécurité : elle évite de proposer une porte que l'utilisateur
 * ne peut pas ouvrir, mais RoleGuard côté Angular et SecurityConfig côté Spring
 * restent les barrières qui décident vraiment.
 */
public enum AssistantRoute {

    // ─── Commun à tous les rôles ──────────────────────────────────────────────
    // Le tableau de bord est propre à chaque rôle : le chemin est complété plus
    // bas par `cheminPour`, car « /dashboard » seul ne correspond à aucune route.
    TABLEAU_DE_BORD("Tableau de bord — vue d'ensemble et statistiques",
            "/dashboard", Set.of("ADMIN", "N1", "N2", "EMPLOYE")),
    PROFIL("Profil : mot de passe, sécurité, reconnaissance faciale, clés d'accès",
            "/profile", Set.of("ADMIN", "N1", "N2", "EMPLOYE")),
    MES_FICHES("Mes fiches d'évaluation reçues",
            "/fiches", Set.of("ADMIN", "N1", "N2", "EMPLOYE")),

    FICHE_DETAIL("Détail d'une fiche d'évaluation précise — indiquer laquelle dans « parametre »",
            "/fiches", Cible.FICHE, Set.of("ADMIN", "N1", "N2", "EMPLOYE")),

    // ─── Administration ───────────────────────────────────────────────────────
    EMPLOYES_LISTE("Liste des employés",
            "/employees", Set.of("ADMIN")),
    EMPLOYE_CREER("Créer un nouvel employé",
            "/employees/create", Set.of("ADMIN")),
    EMPLOYE_DETAIL("Fiche d'un employé précis — indiquer son nom dans « parametre »",
            "/employees", Cible.EMPLOYE, Set.of("ADMIN")),

    // ─── Campagnes d'évaluation ───────────────────────────────────────────────
    CAMPAGNES_LISTE("Liste des campagnes d'évaluation",
            "/evaluations", Set.of("ADMIN", "N1", "N2")),
    CAMPAGNE_CREER("Créer une campagne d'évaluation",
            "/evaluations/create", Set.of("ADMIN")),
    CAMPAGNE_DETAIL("Détail d'une campagne précise — indiquer son nom dans « parametre »",
            "/evaluations", Cible.CAMPAGNE, Set.of("ADMIN", "N1", "N2")),

    // ─── Espace manager (N1) ──────────────────────────────────────────────────
    N1_EVALUER("Évaluer les employés de mon équipe",
            "/n1/evaluer", Set.of("N1")),
    N1_EVALUER_CAMPAGNE("Évaluer mon équipe sur une campagne précise — indiquer son nom dans « parametre »",
            "/n1/evaluer", Cible.CAMPAGNE, Set.of("N1")),
    N1_HISTORIQUE("Historique des évaluations que j'ai soumises",
            "/n1/historique", Set.of("N1")),

    // ─── Validation (N2) ──────────────────────────────────────────────────────
    N2_VALIDER("Valider les évaluations remontées",
            "/n2/valider", Set.of("N2"));

    /**
     * Nature de l'enregistrement qu'une route paramétrée désigne.
     *
     * Elle indique au serveur dans quelle liste du périmètre chercher la
     * correspondance. `AUCUNE` couvre les écrans fixes, qui n'attendent rien.
     */
    public enum Cible { AUCUNE, CAMPAGNE, FICHE, EMPLOYE }

    private final String description;
    private final String chemin;
    private final Cible cible;
    private final Set<String> roles;

    AssistantRoute(String description, String chemin, Set<String> roles) {
        this(description, chemin, Cible.AUCUNE, roles);
    }

    AssistantRoute(String description, String chemin, Cible cible, Set<String> roles) {
        this.description = description;
        this.chemin = chemin;
        this.cible = cible;
        this.roles = roles;
    }

    public String getDescription() {
        return description;
    }

    public String getChemin() {
        return chemin;
    }

    public Cible getCible() {
        return cible;
    }

    /** Vrai si cette route ne mène nulle part sans un enregistrement désigné. */
    public boolean estParametree() {
        return cible != Cible.AUCUNE;
    }

    /**
     * Chemin effectif pour ce rôle.
     *
     * Seul le tableau de bord varie : chaque rôle a le sien
     * (/dashboard/admin, /dashboard/n1, …). Le suffixe est dérivé du rôle lu
     * dans le jeton, jamais d'une valeur fournie par le client.
     */
    public String cheminPour(String role) {
        if (this == TABLEAU_DE_BORD && role != null) {
            return "/dashboard/" + role.toLowerCase();
        }
        return chemin;
    }

    /**
     * Chemin vers un enregistrement précis.
     *
     * L'identifiant vient toujours d'une correspondance trouvée dans le
     * périmètre de l'appelant, jamais de la sortie du modèle : celui-ci ne
     * manipule que des libellés et n'a donc aucun moyen de désigner un
     * enregistrement qu'il n'a pas le droit de voir.
     */
    public String cheminPour(String role, Long identifiant) {
        if (!estParametree() || identifiant == null) {
            return cheminPour(role);
        }
        return chemin + "/" + identifiant;
    }

    public boolean autorisePour(String role) {
        return role != null && roles.contains(role);
    }

    /** Destinations proposables à ce rôle. */
    public static List<AssistantRoute> pour(String role) {
        return Arrays.stream(values()).filter(r -> r.autorisePour(role)).toList();
    }

    /**
     * Résout une clé émise par le modèle, en vérifiant le rôle au passage.
     * Vide si la clé est inconnue ou hors périmètre — dans les deux cas on
     * n'oriente nulle part.
     */
    public static Optional<AssistantRoute> resoudre(String cle, String role) {
        if (cle == null || cle.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(r -> r.name().equalsIgnoreCase(cle.trim()))
                .filter(r -> r.autorisePour(role))
                .findFirst();
    }
}
