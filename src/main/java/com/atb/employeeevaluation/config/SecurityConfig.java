package com.atb.employeeevaluation.config;

import com.atb.employeeevaluation.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    /**
     * Origines autorisées à appeler l'API depuis un navigateur.
     *
     * La liste était écrite en dur sur `localhost`. En ligne, il aurait fallu
     * modifier ce fichier et recompiler pour que le client fonctionne — la
     * tentation étant alors d'ajouter un joker, lequel, avec
     * `allowCredentials(true)`, laisserait n'importe quel site lire les réponses
     * en portant la session de la victime.
     *
     * À renseigner au déploiement via `app.cors.allowed-origins`, avec le
     * domaine exact et le schéma `https`. Le développement garde ses deux ports.
     */
    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
    private String originesAutorisees;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(originesAutorisees.split(","))
                .map(String::trim)
                .filter(origine -> !origine.isEmpty())
                // Le joker est refusé quelles que soient les instructions de
                // déploiement : associé aux identifiants, il vaut « aucune
                // restriction ». Une configuration hâtive ne peut pas l'imposer.
                .filter(origine -> !"*".equals(origine))
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ==================== PUBLIC ====================
                        // Pré-authentification uniquement. L'enrôlement d'un facteur
                        // vit sous /api/mfa/** et exige une session valide, sinon
                        // n'importe qui pourrait enrôler une clé sur un autre compte.
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                         "/api/auth/mfa/**", "/api/auth/recovery/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // /me et /change-password exigent une session
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/mfa/**").authenticated()

                        // ==================== ADMIN ====================
                        // N+1 peut consulter uniquement ses subordonnés directs
                        .requestMatchers(HttpMethod.GET, "/api/employes/role/**").hasAnyRole("ADMIN", "N1", "N2")
                        .requestMatchers(HttpMethod.GET, "/api/employes/sous-n1/**").hasAnyRole("ADMIN", "N1")
                        .requestMatchers(HttpMethod.GET, "/api/employes/sous-n2/**").hasAnyRole("ADMIN", "N2")
                        .requestMatchers(HttpMethod.GET, "/api/employes/*").authenticated()
                        .requestMatchers("/api/employes/**").hasRole("ADMIN")

                        // ==================== ADMIN, N1, N2 ====================
                        // Lecture seule ouverte à l'EMPLOYE (nom de campagne / questions de sa propre fiche)
                        .requestMatchers(HttpMethod.GET, "/api/evaluations/**").hasAnyRole("ADMIN", "N1", "N2", "EMPLOYE")
                        .requestMatchers("/api/evaluations/**").hasAnyRole("ADMIN", "N1", "N2")

                        // ==================== FICHES ====================
                        // L'ORDRE EST SIGNIFICATIF : Spring Security retient la PREMIÈRE
                        // règle qui correspond, pas la plus spécifique. Les règles de rôle
                        // ci-dessous doivent donc précéder le « /api/fiches/** authenticated() ».
                        // Elles étaient placées après : elles ne s'appliquaient jamais, et un
                        // EMPLOYE obtenait un 201 en saisissant sa propre note N+1.
                        .requestMatchers(HttpMethod.POST, "/api/fiches/evaluer").hasAnyRole("ADMIN", "N1")
                        .requestMatchers(HttpMethod.PATCH, "/api/fiches/*/n2").hasAnyRole("ADMIN", "N2")
                        .requestMatchers(HttpMethod.DELETE, "/api/fiches/**").hasAnyRole("ADMIN", "N1")
                        // Le reste exige une session ; la propriété de la ressource est
                        // vérifiée dans FicheEvaluationController, un matcher d'URL ne
                        // pouvant pas exprimer « seulement mes subordonnés ».
                        .requestMatchers("/api/fiches/**").authenticated()

                        // ==================== ACTIVITÉS ====================
                        .requestMatchers(HttpMethod.DELETE, "/api/activites").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}