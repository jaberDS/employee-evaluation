package com.atb.employeeevaluation.config;

import com.atb.employeeevaluation.security.JpaCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class WebAuthnConfig {

    private final JpaCredentialRepository credentialRepository;

    /**
     * Le RP ID est un suffixe de domaine enregistrable de l'hôte : ni le port ni
     * le schéma n'en font partie, donc « localhost » couvre le front (:4200) comme
     * l'API (:8081). Le modifier invaliderait toutes les clés déjà enrôlées.
     */
    @Value("${webauthn.rp-id}")
    private String rpId;

    @Value("${webauthn.rp-name}")
    private String rpName;

    /** Les origines, elles, sont sensibles au port : seule celle d'Angular compte. */
    @Value("${webauthn.origins}")
    private String origins;

    @Bean
    public RelyingParty relyingParty() {
        Set<String> allowedOrigins = new LinkedHashSet<>(Arrays.asList(origins.split("\\s*,\\s*")));

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId)
                        .name(rpName)
                        .build())
                .credentialRepository(credentialRepository)
                .origins(allowedOrigins)
                .allowOriginPort(true)
                .build();
    }
}
