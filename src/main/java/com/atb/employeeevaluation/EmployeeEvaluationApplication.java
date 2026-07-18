package com.atb.employeeevaluation;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.repository.EmployeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmployeeEvaluationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeEvaluationApplication.class, args);
    }

    @Bean
    public CommandLineRunner init(EmployeRepository employeRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            Employe admin = employeRepository.findByMatricule("ADMIN001")
                    .orElseGet(() -> Employe.builder()
                            .matricule("ADMIN001")
                            .email(employeRepository.existsByEmail("admin@banque.com")
                                    ? "admin001@banque.com"
                                    : "admin@banque.com")
                            .build());

            admin.setNom("Dupont");
            admin.setPrenom("Jean");
            admin.setMotDePasse(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ADMIN);
            admin.setActif(true);

            employeRepository.save(admin);
            System.out.println("Admin disponible : ADMIN001 / admin123");
        };
    }
}
