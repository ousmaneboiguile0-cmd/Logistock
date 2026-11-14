package com.logistock.auth;

import com.logistock.security.AuthUser;
import com.logistock.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** F1 : connexion sécurisée par identifiant (e-mail) et mot de passe. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String motDePasse) {
    }

    public record UserInfo(int id, String nom, String email, String role) {
    }

    public record LoginResponse(String token, UserInfo utilisateur) {
    }

    private record LigneUtilisateur(int id, String nom, String email, String hache, boolean actif, String role) {
    }

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse connexion(@Valid @RequestBody LoginRequest requete) {
        List<LigneUtilisateur> lignes = jdbc.query(
                "select u.id_utilisateur, u.nom, u.email, u.mot_de_passe_hache, u.actif, r.libelle "
                        + "from utilisateur u join role r on r.id_role = u.id_role "
                        + "where lower(u.email) = lower(?)",
                (rs, i) -> new LigneUtilisateur(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5), rs.getString(6)),
                requete.email().trim());

        // Même message dans tous les cas d'échec : on ne révèle pas si l'e-mail existe
        if (lignes.isEmpty()
                || !lignes.get(0).actif()
                || !encoder.matches(requete.motDePasse(), lignes.get(0).hache())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides ou compte désactivé");
        }
        LigneUtilisateur u = lignes.get(0);
        String jeton = jwtService.generer(u.id(), u.email(), u.role());
        return new LoginResponse(jeton, new UserInfo(u.id(), u.nom(), u.email(), u.role()));
    }

    @GetMapping("/me")
    public UserInfo moi(@AuthenticationPrincipal AuthUser courant) {
        List<UserInfo> lignes = jdbc.query(
                "select id_utilisateur, nom, email from utilisateur where id_utilisateur = ?",
                (rs, i) -> new UserInfo(rs.getInt(1), rs.getString(2), rs.getString(3), courant.role()),
                courant.id());
        return lignes.get(0);
    }
}
