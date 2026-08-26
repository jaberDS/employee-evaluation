package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.WebAuthnCredential;
import com.atb.employeeevaluation.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pont entre la bibliothèque Yubico et le stockage JPA.
 *
 * Note sur les transports : {@code getCredentialIdsForUsername} n'en déclare
 * volontairement aucun. Une liste filtrée peut masquer l'option « utiliser un
 * téléphone » du navigateur, celle qui déclenche le QR du transport hybride.
 */
@Component
@RequiredArgsConstructor
public class JpaCredentialRepository implements CredentialRepository {

    private final WebAuthnCredentialRepository credentialRepository;

    /** Les valeurs stockées ont été encodées par nous : un échec est une corruption. */
    public static ByteArray decode(String base64Url) {
        try {
            return ByteArray.fromBase64Url(base64Url);
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Donnée WebAuthn corrompue en base", e);
        }
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return credentialRepository.findByEmployeMatriculeAndActifTrueOrderByCreeLeDesc(username).stream()
                .map(c -> PublicKeyCredentialDescriptor.builder()
                        .id(decode(c.getCredentialId()))
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return credentialRepository.findByEmployeMatriculeAndActifTrueOrderByCreeLeDesc(username).stream()
                .findFirst()
                .map(c -> decode(c.getUserHandle()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return credentialRepository.findByUserHandleAndActifTrue(userHandle.getBase64Url()).stream()
                .findFirst()
                .map(c -> c.getEmploye().getMatricule());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentialRepository.findByCredentialIdAndActifTrue(credentialId.getBase64Url())
                .filter(c -> c.getUserHandle().equals(userHandle.getBase64Url()))
                .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentialRepository.findByCredentialIdAndActifTrue(credentialId.getBase64Url())
                .map(this::toRegisteredCredential)
                .map(Set::of)
                .orElseGet(Set::of);
    }

    private RegisteredCredential toRegisteredCredential(WebAuthnCredential c) {
        return RegisteredCredential.builder()
                .credentialId(decode(c.getCredentialId()))
                .userHandle(decode(c.getUserHandle()))
                .publicKeyCose(new ByteArray(c.getPublicKeyCose()))
                .signatureCount(c.getSignatureCount())
                .build();
    }
}
