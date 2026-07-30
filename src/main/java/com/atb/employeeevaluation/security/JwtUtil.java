package com.atb.employeeevaluation.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    /**
     * Type porté par le claim « type ». Il est vérifié à chaque usage : un jeton
     * émis pour une étape MFA ne doit jamais être accepté comme jeton d'accès.
     */
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String TYPE_MFA_PENDING = "mfa_pending";
    public static final String TYPE_RESET = "reset";

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_DECOY = "decoy";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration:86400000}")
    private Long refreshExpiration;

    @Value("${mfa.pending-expiration:300000}")
    private Long mfaPendingExpiration;

    @Value("${mfa.reset-expiration:600000}")
    private Long resetExpiration;

    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ─── Émission ─────────────────────────────────────────────────────────────

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_ACCESS);
        return createToken(claims, username, expiration);
    }

    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_REFRESH);
        return createToken(claims, username, refreshExpiration);
    }

    /**
     * Jeton court « mot de passe vérifié, second facteur en attente ».
     * Le claim {@code purpose} distingue une connexion d'une récupération de compte ;
     * {@code decoy} marque un jeton leurre émis pour un matricule inconnu, afin que
     * la réponse de /recovery/start soit indiscernable.
     */
    public String generateMfaPendingToken(String username, String purpose, boolean decoy) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_MFA_PENDING);
        claims.put(CLAIM_PURPOSE, purpose);
        claims.put(CLAIM_DECOY, decoy);
        claims.put(Claims.ID, UUID.randomUUID().toString());
        return createToken(claims, username, mfaPendingExpiration);
    }

    /** Jeton autorisant uniquement la pose d'un nouveau mot de passe. */
    public String generateResetToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_RESET);
        claims.put(Claims.ID, UUID.randomUUID().toString());
        return createToken(claims, username, resetExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && TYPE_ACCESS.equals(extractType(token));
    }

    public Boolean validateRefreshToken(String token) {
        try {
            return !isTokenExpired(token) && TYPE_REFRESH.equals(extractType(token));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return TYPE_ACCESS.equals(extractType(token));
        } catch (Exception e) {
            return false;
        }
    }

    /** Valide un jeton d'étape MFA pour l'usage attendu et renvoie le matricule. */
    public String validatePendingToken(String token, String expectedPurpose) {
        Claims claims = extractAllClaims(token);
        if (!TYPE_MFA_PENDING.equals(claims.get(CLAIM_TYPE))) {
            throw new IllegalArgumentException("Type de jeton invalide");
        }
        if (!expectedPurpose.equals(claims.get(CLAIM_PURPOSE))) {
            throw new IllegalArgumentException("Usage de jeton invalide");
        }
        return claims.getSubject();
    }

    public String validateResetToken(String token) {
        Claims claims = extractAllClaims(token);
        if (!TYPE_RESET.equals(claims.get(CLAIM_TYPE))) {
            throw new IllegalArgumentException("Type de jeton invalide");
        }
        return claims.getSubject();
    }

    public boolean isDecoy(String token) {
        return Boolean.TRUE.equals(extractAllClaims(token).get(CLAIM_DECOY));
    }

    // ─── Extraction ───────────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    public String extractType(String token) {
        Object type = extractAllClaims(token).get(CLAIM_TYPE);
        return type == null ? null : type.toString();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
