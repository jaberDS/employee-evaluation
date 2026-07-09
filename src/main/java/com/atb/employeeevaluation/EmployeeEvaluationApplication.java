package com.atb.employeeevaluation;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.enums.Role;
import com.atb.employeeevaluation.repository.EmployeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class EmployeeEvaluationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeEvaluationApplication.class, args);
    }

    @Bean
    public CommandLineRunner init(EmployeRepository employeRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (employeRepository.count() == 0) {
                Employe admin = Employe.builder()
                        .matricule("ADMIN001")
                        .nom("Dupont")
                        .prenom("Jean")
                        .email("admin@banque.com")
                        .motDePasse(passwordEncoder.encode("admin123"))
                        .role(Role.ADMIN)
                        .actif(true)
                        .build();
                employeRepository.save(admin);
                System.out.println("✅ Admin créé : ADMIN001 / admin123");
            }
        };
    }
}