package com.atb.employeeevaluation.scheduler;

import com.atb.employeeevaluation.repository.FaceCeremonyRepository;
import com.atb.employeeevaluation.repository.WebAuthnCeremonyRepository;
import com.atb.employeeevaluation.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebAuthnCeremonyCleanupScheduler {

    private final WebAuthnCeremonyRepository ceremonyRepository;
    private final FaceCeremonyRepository faceCeremonyRepository;
    private final RateLimiter rateLimiter;

    /** Toutes les dix minutes : purge des cérémonies périmées et des compteurs vides. */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void purger() {
        LocalDateTime seuil = LocalDateTime.now().minusHours(1);

        int supprimees = ceremonyRepository.deleteExpirees(seuil);
        if (supprimees > 0) {
            log.debug("🧹 {} cérémonie(s) WebAuthn expirée(s) supprimée(s)", supprimees);
        }

        int faciales = faceCeremonyRepository.deleteExpirees(seuil);
        if (faciales > 0) {
            log.debug("🧹 {} cérémonie(s) faciale(s) expirée(s) supprimée(s)", faciales);
        }

        rateLimiter.purger(Duration.ofMinutes(15));
    }
}
