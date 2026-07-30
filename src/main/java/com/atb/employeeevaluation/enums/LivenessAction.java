package com.atb.employeeevaluation.enums;

/**
 * Consignes de vivacité active. Le serveur en tire une séquence aléatoire à
 * chaque cérémonie : c'est ce caractère imprévisible qui fait tomber le rejeu
 * d'une vidéo enregistrée.
 *
 * Les noms sont le contrat partagé avec le microservice Python — les changer
 * exige de changer aussi `_check_action` dans face-service/main.py.
 */
public enum LivenessAction {

    NEUTRAL("Regardez l'objectif, visage neutre", 1200),
    BLINK("Clignez des yeux", 1500),
    TURN_LEFT("Tournez lentement la tête vers la gauche", 1800),
    TURN_RIGHT("Tournez lentement la tête vers la droite", 1800),
    SMILE("Souriez", 1500);

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
