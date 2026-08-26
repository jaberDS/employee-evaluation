package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.repository.EmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeRepository employeRepository;

    @Override
    public UserDetails loadUserByUsername(String matricule) throws UsernameNotFoundException {
        Employe employe = employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new UsernameNotFoundException("Employé non trouvé: " + matricule));

        if (!employe.getActif()) {
            throw new UsernameNotFoundException("Compte désactivé: " + matricule);
        }

        String roleName = "ROLE_" + employe.getRole().name();
        log.info("✅ Chargement de l'utilisateur: {}, rôle: {}", matricule, roleName);

        return new User(
                employe.getMatricule(),
                employe.getMotDePasse(),
                Collections.singletonList(new SimpleGrantedAuthority(roleName))
        );
    }
}