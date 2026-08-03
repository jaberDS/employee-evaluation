package com.atb.employeeevaluation;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.repository.EmployeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.SecureRandom;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class EmployeeEvaluationApplication {

    private static final String MATRICULE_ADMIN = "ADMIN001";

    public static void main(String[] args) {
        SpringApplication.run(EmployeeEvaluationApplication.class, args);
    }

    /**
     * Amorçage du premier administrateur, pour qu'une base vierge soit
     * utilisable.
     *
     * Cette méthode réécrivait le mot de passe de ADMIN001 à « admin123 » à
     * **chaque** démarrage, et l'affichait sur la sortie standard. Autrement dit :
     * l'administrateur pouvait changer son mot de passe, le prochain redémarrage
     * le remettait à une valeur publique — et la journalisation du serveur la
     * conservait par écrit. C'était la voie d'entrée la plus courte de toute
     * l'application.
     *
     * Trois règles désormais :
     *
     * <ul>
     *   <li>si le compte existe, on n'y touche pas — ni mot de passe, ni rôle ;</li>
     *   <li>le mot de passe initial vient de la configuration
     *       ({@code app.admin.initial-password}, à poser par variable
     *       d'environnement) ; à défaut il est tiré au hasard ;</li>
     *   <li>il n'est affiché que s'il a été tiré au hasard, une seule fois, à la
     *       création — sans quoi personne ne pourrait se connecter.</li>
     * </ul>
     */
    @Bean
    public CommandLineRunner init(EmployeRepository employeRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${app.admin.initial-password:}") String motDePasseConfigure) {
        return args -> {
            if (employeRepository.findByMatricule(MATRICULE_ADMIN).isPresent()) {
                log.info("Compte {} déjà présent : aucun amorçage.", MATRICULE_ADMIN);
                return;
            }

            boolean tireAuHasard = motDePasseConfigure == null || motDePasseConfigure.isBlank();
            String motDePasse = tireAuHasard ? motDePasseAleatoire() : motDePasseConfigure;

            Employe admin = Employe.builder()
                    .matricule(MATRICULE_ADMIN)
                    .email(employeRepository.existsByEmail("admin@banque.com")
                            ? "admin001@banque.com"
                            : "admin@banque.com")
                    .nom("Dupont")
                    .prenom("Jean")
                    .motDePasse(passwordEncoder.encode(motDePasse))
                    .role(Role.ADMIN)
                    .actif(true)
                    .build();

            employeRepository.save(admin);

            if (tireAuHasard) {
                log.warn("Compte {} créé. Mot de passe provisoire : {}", MATRICULE_ADMIN, motDePasse);
                log.warn("À changer dès la première connexion. Définissez "
                        + "app.admin.initial-password pour éviter cet affichage.");
            } else {
                log.info("Compte {} créé avec le mot de passe fourni par la configuration.",
                        MATRICULE_ADMIN);
            }
        };
    }

    /**
     * Mot de passe provisoire conforme à la politique : une majuscule, une
     * minuscule, un chiffre et un caractère spécial au minimum.
     */
    private String motDePasseAleatoire() {
        final String majuscules = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String minuscules = "abcdefghijkmnopqrstuvwxyz";
        final String chiffres = "23456789";
        final String speciaux = "!@#$%&*?";
        final String tout = majuscules + minuscules + chiffres + speciaux;

        SecureRandom alea = new SecureRandom();
        StringBuilder motDePasse = new StringBuilder();

        // Un de chaque classe d'abord : garantit le respect de la politique.
        motDePasse.append(majuscules.charAt(alea.nextInt(majuscules.length())));
        motDePasse.append(minuscules.charAt(alea.nextInt(minuscules.length())));
        motDePasse.append(chiffres.charAt(alea.nextInt(chiffres.length())));
        motDePasse.append(speciaux.charAt(alea.nextInt(speciaux.length())));

        while (motDePasse.length() < 16) {
            motDePasse.append(tout.charAt(alea.nextInt(tout.length())));
        }
        return motDePasse.toString();
    }
}
