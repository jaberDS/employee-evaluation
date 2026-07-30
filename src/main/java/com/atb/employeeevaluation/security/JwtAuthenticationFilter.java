package com.atb.employeeevaluation.security;

import com.atb.employeeevaluation.entity.Employe;
import com.atb.employeeevaluation.repository.EmployeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final EmployeRepository employeRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        log.debug("🔍 Auth Header: {}", authHeader);

        String username = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                // Un jeton d'étape MFA ou de réinitialisation ne doit jamais
                // authentifier une requête : on l'écarte avant toute résolution.
                if (jwtUtil.isAccessToken(jwt)) {
                    username = jwtUtil.extractUsername(jwt);
                    log.debug("👤 Username extrait: {}", username);
                } else {
                    log.debug("↩️ Jeton non-accès ignoré par le filtre");
                }
            } catch (Exception e) {
                log.error("❌ Erreur extraction username: {}", e.getMessage());
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                log.debug("✅ UserDetails chargé: {}", userDetails.getUsername());
                log.debug("✅ Autorités: {}", userDetails.getAuthorities());

                if (jwtUtil.validateToken(jwt, userDetails) && !emisAvantChangementMotDePasse(jwt, username)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("✅ Authentification réussie pour: {}", username);
                } else {
                    log.warn("⚠️ Token JWT invalide pour: {}", username);
                }
            } catch (Exception e) {
                log.error("❌ Erreur lors de l'authentification: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Un mot de passe changé invalide les jetons émis avant. La blacklist en mémoire
     * de AuthServiceImpl n'étant pas consultée ici, c'est ce contrôle qui garantit
     * qu'une session ouverte ne survit pas à une réinitialisation.
     */
    private boolean emisAvantChangementMotDePasse(String jwt, String matricule) {
        try {
            LocalDateTime modifieLe = employeRepository.findByMatricule(matricule)
                    .map(Employe::getMotDePasseModifieLe)
                    .orElse(null);
            if (modifieLe == null) {
                return false;
            }
            LocalDateTime emisLe = LocalDateTime.ofInstant(
                    jwtUtil.extractIssuedAt(jwt).toInstant(), ZoneId.systemDefault());
            // Tolérance d'une seconde : « iat » est arrondi à la seconde.
            return emisLe.isBefore(modifieLe.minusSeconds(1));
        } catch (Exception e) {
            log.error("❌ Erreur contrôle date de mot de passe: {}", e.getMessage());
            return false;
        }
    }
}