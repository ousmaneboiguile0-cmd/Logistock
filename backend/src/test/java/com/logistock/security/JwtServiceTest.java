package com.logistock.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "secret-de-test-assez-long-pour-hmac-sha256-0123456789";

    @Test
    void unJetonValideContientLIdentiteEtLeRole() {
        JwtService service = new JwtService(SECRET, 60);
        Claims claims = service.lire(service.generer(7, "awa@logistock.test", Roles.GESTIONNAIRE));

        assertEquals("awa@logistock.test", claims.getSubject());
        assertEquals(7, claims.get("uid", Integer.class));
        assertEquals(Roles.GESTIONNAIRE, claims.get("role", String.class));
    }

    @Test
    void unJetonFalsifieEstRefuse() {
        JwtService service = new JwtService(SECRET, 60);
        String jeton = service.generer(1, "a@b.c", Roles.ADMIN);
        String falsifie = jeton.substring(0, jeton.length() - 3) + "abc";

        assertThrows(JwtException.class, () -> service.lire(falsifie));
    }

    @Test
    void unJetonSigneAvecUnAutreSecretEstRefuse() {
        JwtService emetteur = new JwtService("un-autre-secret-completement-different-0123456789", 60);
        JwtService verificateur = new JwtService(SECRET, 60);

        assertThrows(JwtException.class, () -> verificateur.lire(emetteur.generer(1, "a@b.c", Roles.ADMIN)));
    }

    @Test
    void unJetonExpireEstRefuse() {
        JwtService service = new JwtService(SECRET, -1);

        assertThrows(JwtException.class, () -> service.lire(service.generer(1, "a@b.c", Roles.ADMIN)));
    }
}
