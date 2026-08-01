package com.atb.employeeevaluation.enums;

/**
 * Consignes de vivacité active. Le serveur en tire une séquence aléatoire à
 * chaque cérémonie : c'est ce caractère imprévisible qui fait tomber le rejeu
 * d'une vidéo enregistrée.
 *
 * Les durées couvrent le temps de LIRE la consigne puis de l'exécuter. Elles
 * étaient calées sur le seul geste, ce qui ne laissait pas le temps de réagir :
 * la consigne disparaissait avant que le mouvement ne soit fait, et la vivacité
 * échouait alors même que l'utilisateur coopérait.
 *
 * Les noms sont le contrat partagé avec le microservice Python — les changer
 * exige de changer aussi `_check_slot` dans face-service/main.py.
 */
public enum LivenessAction {

    NEUTRAL("Regardez l'objectif, visage neutre", 2200),
    BLINK("Clignez des yeux", 3200),
    // Une rotation part du repos, pivote, puis revient : le geste complet est
    // sensiblement plus long qu'un clignement.
    TURN_LEFT("Tournez lentement la tête vers la gauche", 3800),
    TURN_RIGHT("Tournez lentement la tête vers la droite", 3800),
    SMILE("Souriez", 3200);

    private final String instruction;
    private final int dureeMs;

    LivenessAction(String instruction, int dureeMs) {
        this.instruction = instruction;
        this.dureeMs = dureeMs;
    }

    public String getInstruction() {
        return instruction;
    }

    public int getDureeMs() {
        return dureeMs;
    }
}
