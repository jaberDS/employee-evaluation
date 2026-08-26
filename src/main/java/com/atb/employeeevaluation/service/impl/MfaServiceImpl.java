package com.atb.employeeevaluation.service.impl;

import com.atb.employeeevaluation.dto.AuthResponse;
import com.atb.employeeevaluation.dto.CeremonyOptionsResponse;
import com.atb.employeeevaluation.dto.FaceChallengeResponse;
import com.atb.employeeevaluation.dto.FaceVerifyRequest;
import com.atb.employeeevaluation.dto.LoginResponse;
import com.atb.employeeevaluation.dto.MfaVerifyRequest;
import com.atb.employeeevaluation.dto.ResetPasswordRequest;
import com.atb.employeeevaluation.enums.AuthenticatorPreference;
import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.MfaPurpose;
import com.atb.employeeevaluation.enums.TypeActivite;
import com.atb.employeeevaluation.exception.AuthenticationFailedException;
import com.atb.employeeevaluation.repository.EmployeRepository;
import com.atb.employeeevaluation.security.JwtUtil;
import com.atb.employeeevaluation.security.RateLimiter;
import com.atb.employeeevaluation.service.ActiviteLogService;
import com.atb.employeeevaluation.service.FaceService;
import com.atb.employeeevaluation.service.MfaService;
import com.atb.employeeevaluation.service.WebAuthnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MfaServiceImpl implements MfaService {

    private static final Duration FENETRE = Duration.ofMinutes(15);
    private static final int MAX_PAR_MATRICULE = 5;
    private static final int MAX_PAR_IP = 20;

    private final WebAuthnService webAuthnService;
    private final FaceService faceService;
    private final JwtUtil jwtUtil;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final ActiviteLogService activiteLogService;

    // ─── Connexion ────────────────────────────────────────────────────────────

    @Override
    public CeremonyOptionsResponse startLoginAssertion(String mfaToken, AuthenticatorPreference preference) {
        String matricule = lireJetonEtape(mfaToken, MfaPurpose.LOGIN_2FA);
        return webAuthnService.startAssertion(matricule, MfaPurpose.LOGIN_2FA, false, preference);
    }

    @Override
    public LoginResponse finishLoginAssertion(MfaVerifyRequest request) {
        String matricule = lireJetonEtape(request.getMfaToken(), MfaPurpose.LOGIN_2FA);
        rateLimiter.verifier("mfa:" + matricule, MAX_PAR_MATRICULE, FENETRE);

        String prouve = webAuthnService.finishAssertion(
                request.getCeremonyId(), request.getCredential(), MfaPurpose.LOGIN_2FA);

        // L'assertion prouve une identité : elle doit correspondre au compte du jeton.
        if (!matricule.equals(prouve)) {
            throw new AuthenticationFailedException("La clé d'accès ne correspond pas à ce compte");
        }

        rateLimiter.reinitialiser("mfa:" + matricule);
        Employe employe = employeActif(matricule);
        activiteLogService.log(TypeActivite.MFA_VALIDEE, "Connexion validée par clé d'accès");

        return LoginResponse.builder()
                .mfaRequired(false)
                .session(creerSession(employe))
                .build();
    }

    // ─── Connexion par reconnaissance faciale ─────────────────────────────────

    @Override
    public FaceChallengeResponse startLoginFace(String mfaToken) {
        String matricule = lireJetonEtape(mfaToken, MfaPurpose.LOGIN_2FA);
        return faceService.startCeremony(matricule, MfaPurpose.LOGIN_2FA, false);
    }

    @Override
    public LoginResponse finishLoginFace(FaceVerifyRequest request) {
        String matricule = lireJetonEtape(request.getMfaToken(), MfaPurpose.LOGIN_2FA);
        rateLimiter.verifier("mfa:" + matricule, MAX_PAR_MATRICULE, FENETRE);

        faceService.verify(matricule, request, MfaPurpose.LOGIN_2FA);

        rateLimiter.reinitialiser("mfa:" + matricule);
        Employe employe = employeActif(matricule);
        activiteLogService.log(TypeActivite.MFA_VALIDEE, "Connexion validée par reconnaissance faciale");

        return LoginResponse.builder()
                .mfaRequired(false)
                .session(creerSession(employe))
                .build();
    }

    // ─── Récupération de compte ───────────────────────────────────────────────

    @Override
    public LoginResponse startRecovery(String matricule, String clientIp) {
        rateLimiter.verifier("recovery-ip:" + clientIp, MAX_PAR_IP, FENETRE);
        rateLimiter.verifier("recovery:" + matricule, MAX_PAR_MATRICULE, FENETRE);

        // Un compte inconnu, désactivé ou sans facteur donne un jeton leurre : la
        // réponse est identique dans tous les cas, ce qui interdit l'énumération.
        boolean eligible = employeRepository.findByMatricule(matricule)
                .filter(Employe::getActif)
                .map(e -> webAuthnService.hasCredentials(matricule) || faceService.hasTemplate(matricule))
                .orElse(false);

        // La liste des facteurs est volontairement fixe : annoncer les facteurs
        // réellement enrôlés révélerait la configuration du compte.
        return LoginResponse.builder()
                .mfaRequired(true)
                .mfaToken(jwtUtil.generateMfaPendingToken(
                        matricule, MfaPurpose.PASSWORD_RESET.name(), !eligible))
                .factors(List.of("WEBAUTHN", "FACE"))
                .build();
    }

    @Override
    public CeremonyOptionsResponse startRecoveryAssertion(String mfaToken, AuthenticatorPreference preference) {
        String matricule = lireJetonEtape(mfaToken, MfaPurpose.PASSWORD_RESET);
        boolean decoy = jwtUtil.isDecoy(mfaToken);
        return webAuthnService.startAssertion(matricule, MfaPurpose.PASSWORD_RESET, decoy, preference);
    }

    @Override
    public String finishRecoveryAssertion(MfaVerifyRequest request) {
        String matricule = lireJetonEtape(request.getMfaToken(), MfaPurpose.PASSWORD_RESET);
        rateLimiter.verifier("recovery:" + matricule, MAX_PAR_MATRICULE, FENETRE);

        if (jwtUtil.isDecoy(request.getMfaToken())) {
            throw new AuthenticationFailedException("Vérification de la clé d'accès échouée");
        }

        String prouve = webAuthnService.finishAssertion(
                request.getCeremonyId(), request.getCredential(), MfaPurpose.PASSWORD_RESET);

        if (!matricule.equals(prouve)) {
            throw new AuthenticationFailedException("La clé d'accès ne correspond pas à ce compte");
        }

        employeActif(matricule);
        rateLimiter.reinitialiser("recovery:" + matricule);
        return jwtUtil.generateResetToken(matricule);
    }

    @Override
    public FaceChallengeResponse startRecoveryFace(String mfaToken) {
        String matricule = lireJetonEtape(mfaToken, MfaPurpose.PASSWORD_RESET);
        boolean decoy = jwtUtil.isDecoy(mfaToken);
        return faceService.startCeremony(matricule, MfaPurpose.PASSWORD_RESET, decoy);
    }

    @Override
    public String finishRecoveryFace(FaceVerifyRequest request) {
        String matricule = lireJetonEtape(request.getMfaToken(), MfaPurpose.PASSWORD_RESET);
        rateLimiter.verifier("recovery:" + matricule, MAX_PAR_MATRICULE, FENETRE);

        // La cérémonie leurre échoue d'elle-même dans FaceService : on la laisse
        // se dérouler pour que le temps de réponse ne trahisse rien.
        faceService.verify(matricule, request, MfaPurpose.PASSWORD_RESET);

        employeActif(matricule);
        rateLimiter.reinitialiser("recovery:" + matricule);
        return jwtUtil.generateResetToken(matricule);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String matricule;
        try {
            matricule = jwtUtil.validateResetToken(request.getResetToken());
        } catch (Exception e) {
            throw new AuthenticationFailedException("Jeton de réinitialisation invalide ou expiré", e);
        }

        Employe employe = employeActif(matricule);
        if (passwordEncoder.matches(request.getNewPassword(), employe.getMotDePasse())) {
            throw new AuthenticationFailedException("Le nouveau mot de passe doit différer de l'ancien");
        }

        employe.setMotDePasse(passwordEncoder.encode(request.getNewPassword()));
        // Coupe les sessions ouvertes : JwtAuthenticationFilter rejette les jetons antérieurs.
        employe.setMotDePasseModifieLe(LocalDateTime.now());
        employeRepository.save(employe);

        activiteLogService.log(TypeActivite.MOT_DE_PASSE_REINITIALISE,
                "Mot de passe réinitialisé par clé d'accès — " + matricule);
        log.info("Mot de passe réinitialisé pour: {}", matricule);
    }

    // ─── Interne ──────────────────────────────────────────────────────────────

    private String lireJetonEtape(String mfaToken, MfaPurpose purpose) {
        try {
            return jwtUtil.validatePendingToken(mfaToken, purpose.name());
        } catch (Exception e) {
            throw new AuthenticationFailedException("Session de vérification expirée. Recommencez.", e);
        }
    }

    /** Un compte désactivé ne doit pas pouvoir être récupéré ni connecté. */
    private Employe employeActif(String matricule) {
        return employeRepository.findByMatricule(matricule)
                .filter(Employe::getActif)
                .orElseThrow(() -> new AuthenticationFailedException("Compte indisponible"));
    }

    private AuthResponse creerSession(Employe employe) {
        String token = jwtUtil.generateToken(employe.getMatricule());
        return new AuthResponse(
                employe.getId(),
                token,
                jwtUtil.generateRefreshToken(employe.getMatricule()),
                employe.getMatricule(),
                employe.getNom(),
                employe.getPrenom(),
                employe.getRole().name(),
                jwtUtil.extractExpiration(token).getTime()
        );
    }
}
