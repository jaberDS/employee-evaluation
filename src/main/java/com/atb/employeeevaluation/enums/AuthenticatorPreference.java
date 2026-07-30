package com.atb.employeeevaluation.enums;

import com.yubico.webauthn.data.PublicKeyCredentialHint;

/**
 * Type d'appareil que l'utilisateur veut enrôler ou utiliser.
 *
 * Sans indication, Windows présente d'emblée Windows Hello et enterre l'option
 * « téléphone » derrière un sous-menu que peu de gens trouvent. Le hint WebAuthn
 * L3 inverse cet ordre : avec HYBRID, le navigateur affiche directement le QR
 * code à scanner, et l'empreinte du téléphone signe le challenge.
 */
public enum AuthenticatorPreference {

    /** Windows Hello, Touch ID — le capteur intégré à cet ordinateur. */
    THIS_DEVICE(PublicKeyCredentialHint.CLIENT_DEVICE),

    /** QR code + empreinte du téléphone (transport hybride caBLE v2). */
    PHONE(PublicKeyCredentialHint.HYBRID),

    /** Clé physique type YubiKey. */
    SECURITY_KEY(PublicKeyCredentialHint.SECURITY_KEY),

    /** Aucun hint : le navigateur choisit son ordre habituel. */
    ANY();

    private final PublicKeyCredentialHint[] hints;

    AuthenticatorPreference(PublicKeyCredentialHint... hints) {
        this.hints = hints;
    }

    /** Le builder Yubico n'accepte que des varargs, pas une List. */
    public PublicKeyCredentialHint[] getHints() {
        return hints.clone();
    }
}

