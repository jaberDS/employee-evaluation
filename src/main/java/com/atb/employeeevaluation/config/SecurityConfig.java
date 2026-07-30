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
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200", "http://localhost:3000"));
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

                        // ==================== TOUS LES UTILISATEURS AUTHENTIFIÉS ====================
                        .requestMatchers(HttpMethod.DELETE, "/api/fiches/**").hasAnyRole("ADMIN", "N1")
                        .requestMatchers("/api/fiches/**").authenticated()
                        .requestMatchers("/api/fiches/evaluer").hasAnyRole("ADMIN", "N1")
                        .requestMatchers("/api/fiches/*/n2").hasAnyRole("ADMIN", "N2")
                        .requestMatchers("/api/fiches/employe/**").authenticated()

                        // ==================== ACTIVITÉS ====================
                        .requestMatchers(HttpMethod.DELETE, "/api/activites").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}