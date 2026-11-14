package com.logistock.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lit le jeton "Authorization: Bearer ..." et authentifie la requête.
 * Le rôle et le statut actif sont relus en base à chaque requête : un compte
 * désactivé ou un changement de rôle prend effet immédiatement.
 * Ce filtre n'est volontairement pas un bean Spring : il est créé dans SecurityConfig,
 * pour ne s'exécuter qu'une fois, dans la chaîne de sécurité.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthFilter(JwtService jwtService, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String entete = request.getHeader("Authorization");
        if (entete != null && entete.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.lire(entete.substring(7));
                int id = claims.get("uid", Integer.class);
                List<String> roles = jdbc.queryForList(
                        "select r.libelle from utilisateur u join role r on r.id_role = u.id_role "
                                + "where u.id_utilisateur = ? and u.actif",
                        String.class, id);
                if (!roles.isEmpty()) {
                    AuthUser utilisateur = new AuthUser(id, claims.getSubject(), roles.get(0));
                    UsernamePasswordAuthenticationToken authentification =
                            new UsernamePasswordAuthenticationToken(
                                    utilisateur, null,
                                    List.of(new SimpleGrantedAuthority(Roles.authority(utilisateur.role()))));
                    SecurityContextHolder.getContext().setAuthentication(authentification);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Jeton invalide ou expiré : la requête reste non authentifiée (401)
            }
        }
        chain.doFilter(request, response);
    }
}
