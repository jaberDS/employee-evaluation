package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.CeremonyOptionsResponse;
import com.atb.employeeevaluation.dto.CredentialSummaryDTO;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.entity.WebAuthnCeremony;
import com.atb.employeeevaluation.entity.WebAuthnCredential;
import com.atb.employeeevaluation.enums.AuthenticatorPreference;
import com.atb.employeeevaluation.enums.CeremonyType;
import com.atb.employeeevaluation.enums.MfaPurpose;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.exception.AuthenticationFailedException;
import com.atb.employeeevaluation.exception.ResourceNotFoundException;
import com.atb.employeeevaluation.exception.UnauthorizedOperationException;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.repository.WebAuthnCeremonyRepository;
import com.atb.employeeevaluation.repository.WebAuthnCredentialRepository;
import com.atb.employeeevaluation.service.ActiviteLogService;
import com.atb.employeeevaluation.service.WebAuthnService;
import com.atb.employeeevaluation.security.JpaCredentialRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WebAuthnServiceImpl implements WebAuthnService {

    private final RelyingParty relyingParty;
    private final WebAuthnCredentialRepository credentialRepository;
    private final WebAuthnCeremonyRepository ceremonyRepository;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActiviteLogService activiteLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    @Value("${webauthn.timeout-ms:120000}")
    private long timeoutMs;

    // ─── Enrôlement ───────────────────────────────────────────────────────────

    @Override
    public CeremonyOptionsResponse startRegistration(String matricule, AuthenticatorPreference preference) {
        Employe employe = getEmploye(matricule);

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(employe.getMatricule())
                                .displayName(employe.getPrenom() + " " + employe.getNom())
                                .id(userHandleFor(matricule))
                                .build())
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                // Pas d'authenticatorAttachment : c'est ce qui laisse le
                                // navigateur proposer « utiliser un téléphone » (QR hybride).
                                .residentKey(ResidentKeyRequirement.PREFERRED)
                                .userVerification(UserVerificationRequirement.REQUIRED)
                                .build())
                        // Le hint met en avant le type d'appareil demandé : avec HYBRID,
                        // le navigateur affiche le QR sans le cacher sous un sous-menu.
                        .hints(preference.getHints())
                        .timeout(timeoutMs)
                        .build());

        String optionsJson = serialiser(options::toJson);
        String ceremonyId = persistCeremony(matricule, CeremonyType.REGISTRATION,
                MfaPurpose.ENROLLMENT, optionsJson);

        return new CeremonyOptionsResponse(ceremonyId, optionsJson);
    }

    @Override
    public void finishRegistration(String matricule, String ceremonyId, String label, String credentialJson) {
        WebAuthnCeremony ceremony = consumeCeremony(ceremonyId, CeremonyType.REGISTRATION);
        if (!ceremony.getMatricule().equals(matricule)) {
            throw new UnauthorizedOperationException("Cérémonie liée à un autre compte");
        }

        Employe employe = getEmploye(matricule);

        try {
            PublicKeyCredentialCreationOptions request =
                    PublicKeyCredentialCreationOptions.fromJson(ceremony.getRequestJson());

            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(request)
                            .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                            .build());

            credentialRepository.save(WebAuthnCredential.builder()
                    .employe(employe)
                    .credentialId(result.getKeyId().getId().getBase64Url())
                    .userHandle(request.getUser().getId().getBase64Url())
                    .publicKeyCose(result.getPublicKeyCose().getBytes())
                    .signatureCount(result.getSignatureCount())
                    .aaguid(result.getAaguid().getHex())
                    .transports(result.getKeyId().getTransports()
                            .map(t -> t.stream()
                                    .map(AuthenticatorTransport::getId)
                                    .collect(Collectors.joining(",")))
                            .orElse(null))
                    .label(label)
                    .backupEligible(result.isBackupEligible())
                    .backedUp(result.isBackedUp())
                    .actif(true)
                    .build());

            activiteLogService.log(TypeActivite.PASSKEY_ENREGISTREE,
                    "Nouvelle clé d'accès enregistrée — " + label);

        } catch (RegistrationFailedException | IOException | IllegalArgumentException e) {
            log.warn("Échec d'enrôlement WebAuthn pour {}: {}", matricule, e.getMessage());
            throw new AuthenticationFailedException("L'enregistrement de la clé a échoué", e);
        }
    }

    // ─── Assertion ────────────────────────────────────────────────────────────

    @Override
    public CeremonyOptionsResponse startAssertion(String matricule, MfaPurpose purpose, boolean decoy,
                                                  AuthenticatorPreference preference) {
        AssertionRequest request = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(decoy ? null : matricule)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .hints(preference.getHints())
                        .timeout(timeoutMs)
                        .build());

        String optionsJson = decoy
                ? withFakeCredentials(request)
                : optionsPourNavigateur(serialiser(request::toCredentialsGetJson));

        String ceremonyId = persistCeremony(matricule, CeremonyType.AUTHENTICATION,
                purpose, serialiser(request::toJson));

        return new CeremonyOptionsResponse(ceremonyId, optionsJson);
    }

    @Override
    public String finishAssertion(String ceremonyId, String credentialJson, MfaPurpose purpose) {
        WebAuthnCeremony ceremony = consumeCeremony(ceremonyId, CeremonyType.AUTHENTICATION);
        if (ceremony.getPurpose() != purpose) {
            throw new AuthenticationFailedException("Cérémonie destinée à un autre usage");
        }

        try {
            AssertionResult result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(AssertionRequest.fromJson(ceremony.getRequestJson()))
                            .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                            .build());

            if (!result.isSuccess()) {
                throw new AuthenticationFailedException("Vérification de la clé d'accès échouée");
            }

            // Le compteur de signature doit croître : c'est la protection anti-clonage.
            credentialRepository.findByCredentialIdAndActifTrue(result.getCredentialId().getBase64Url())
                    .ifPresent(c -> {
                        c.setSignatureCount(result.getSignatureCount());
                        c.setDerniereUtilisation(LocalDateTime.now());
                        credentialRepository.save(c);
                    });

            return result.getUsername();

        } catch (AssertionFailedException | IOException | IllegalArgumentException e) {
            log.warn("Échec d'assertion WebAuthn (cérémonie {}): {}", ceremonyId, e.getMessage());
            activiteLogService.log(TypeActivite.MFA_ECHOUEE,
                    "Échec de vérification de la clé d'accès — " + ceremony.getMatricule());
            throw new AuthenticationFailedException("Vérification de la clé d'accès échouée", e);
        }
    }

    // ─── Gestion des clés ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CredentialSummaryDTO> listCredentials(String matricule) {
        return credentialRepository.findByEmployeMatriculeAndActifTrueOrderByCreeLeDesc(matricule).stream()
                .map(c -> CredentialSummaryDTO.builder()
                        .id(c.getId())
                        .label(c.getLabel())
                        .transports(c.getTransports())
                        .creeLe(c.getCreeLe())
                        .derniereUtilisation(c.getDerniereUtilisation())
                        .backedUp(c.getBackedUp())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void renameCredential(String matricule, Long credentialId, String label) {
        WebAuthnCredential credential = credentialRepository
                .findByIdAndEmployeMatricule(credentialId, matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Clé d'accès introuvable"));
        credential.setLabel(label);
        credentialRepository.save(credential);
        activiteLogService.log(TypeActivite.PASSKEY_RENOMMEE, "Clé d'accès renommée — " + label);
    }

    @Override
    public void deleteCredential(String matricule, Long credentialId, String currentPassword) {
        Employe employe = getEmploye(matricule);
        // Retirer un facteur affaiblit le compte : on re-authentifie l'utilisateur.
        if (!passwordEncoder.matches(currentPassword, employe.getMotDePasse())) {
            throw new UnauthorizedOperationException("Le mot de passe actuel est incorrect");
        }

        WebAuthnCredential credential = credentialRepository
                .findByIdAndEmployeMatricule(credentialId, matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Clé d'accès introuvable"));

        credential.setActif(false);
        credentialRepository.save(credential);
        activiteLogService.log(TypeActivite.PASSKEY_SUPPRIMEE,
                "Clé d'accès supprimée — " + credential.getLabel());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCredentials(String matricule) {
        return credentialRepository.existsByEmployeMatriculeAndActifTrue(matricule);
    }

    // ─── Interne ──────────────────────────────────────────────────────────────

    private Employe getEmploye(String matricule) {
        return employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResourceNotFoundException("Employé non trouvé: " + matricule));
    }

    /**
     * Identifiant opaque stable par employé : réutilisé si une clé existe déjà,
     * pour que toutes les clés d'un même compte partagent le même user handle.
     */
    private ByteArray userHandleFor(String matricule) {
        return credentialRepository.findByEmployeMatriculeAndActifTrueOrderByCreeLeDesc(matricule).stream()
                .findFirst()
                .map(c -> JpaCredentialRepository.decode(c.getUserHandle()))
                .orElseGet(() -> {
                    byte[] handle = new byte[32];
                    random.nextBytes(handle);
                    return new ByteArray(handle);
                });
    }

    private String persistCeremony(String matricule, CeremonyType type, MfaPurpose purpose, String requestJson) {
        String ceremonyId = UUID.randomUUID().toString();
        ceremonyRepository.save(WebAuthnCeremony.builder()
                .ceremonyId(ceremonyId)
                .matricule(matricule)
                .type(type)
                .purpose(purpose)
                .requestJson(requestJson)
                .expireLe(LocalDateTime.now().plusNanos(timeoutMs * 1_000_000))
                .consomme(false)
                .build());
        return ceremonyId;
    }

    /** Une cérémonie ne sert qu'une fois : la consommer coupe le rejeu. */
    private WebAuthnCeremony consumeCeremony(String ceremonyId, CeremonyType attendu) {
        WebAuthnCeremony ceremony = ceremonyRepository.findByCeremonyIdAndConsommeFalse(ceremonyId)
                .orElseThrow(() -> new AuthenticationFailedException("Cérémonie inconnue ou déjà utilisée"));

        if (ceremony.getType() != attendu) {
            throw new AuthenticationFailedException("Type de cérémonie inattendu");
        }
        if (ceremony.getExpireLe().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("Cérémonie expirée");
        }

        ceremony.setConsomme(true);
        return ceremonyRepository.save(ceremony);
    }

    /**
     * Réponse leurre : un vrai challenge, mais des identifiants de clés inventés.
     * Le navigateur échouera comme face à un appareil non enrôlé, sans révéler si
     * le matricule existe.
     */
    private String withFakeCredentials(AssertionRequest request) {
        byte[] faux = new byte[32];
        random.nextBytes(faux);
        List<PublicKeyCredentialDescriptor> leurres = List.of(
                PublicKeyCredentialDescriptor.builder().id(new ByteArray(faux)).build());

        AssertionRequest leurre = request.toBuilder()
                .publicKeyCredentialRequestOptions(
                        request.getPublicKeyCredentialRequestOptions().toBuilder()
                                .allowCredentials(leurres)
                                .build())
                .build();

        return optionsPourNavigateur(serialiser(leurre::toCredentialsGetJson));
    }

    /**
     * Retire l'enveloppe {"publicKey": {...}} produite par toCredentialsGetJson.
     *
     * Cette forme est celle attendue par navigator.credentials.get() en JS brut,
     * mais @simplewebauthn/browser veut l'objet intérieur : sans ce déballage il
     * cherche `challenge` à la racine, ne trouve rien, et échoue sur un
     * « Cannot read properties of undefined (reading 'replace') ».
     */
    private String optionsPourNavigateur(String json) {
        try {
            JsonNode racine = objectMapper.readTree(json);
            JsonNode interne = racine.get("publicKey");
            return interne == null ? json : objectMapper.writeValueAsString(interne);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Options WebAuthn illisibles", e);
        }
    }

    /**
     * Sérialisation d'objets que nous venons de construire : un échec Jackson ici
     * signalerait un défaut de la bibliothèque, pas une entrée client invalide.
     */
    private String serialiser(JsonSupplier supplier) {
        try {
            return supplier.get();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation WebAuthn impossible", e);
        }
    }

    @FunctionalInterface
    private interface JsonSupplier {
        String get() throws JsonProcessingException;
    }
}
