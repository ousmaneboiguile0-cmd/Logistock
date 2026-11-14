package com.logistock.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Création et vérification des jetons JWT (signature HMAC-SHA256). */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generer(int idUtilisateur, String email, String role) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("uid", idUtilisateur)
                .claim("role", role)
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Lève une JwtException si le jeton est falsifié, mal formé ou expiré. */
    public Claims lire(String jeton) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(jeton).getPayload();
    }
}
