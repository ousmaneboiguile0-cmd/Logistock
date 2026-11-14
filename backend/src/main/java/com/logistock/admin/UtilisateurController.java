package com.logistock.admin;

import com.logistock.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** F1 : gestion des comptes et des droits, réservée à l'Administrateur. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class UtilisateurController {

    public record Role(int id, String libelle) {
    }

    public record Utilisateur(int id, String nom, String email, boolean actif, int idRole, String role) {
    }

    public record CreationRequest(
            @NotBlank @Size(max = 100) String nom,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String motDePasse,
            @NotNull Integer idRole) {
    }

    public record ModificationRequest(
            @NotBlank @Size(max = 100) String nom,
            @NotNull Integer idRole,
            @NotNull Boolean actif) {
    }

    public record MotDePasseRequest(@NotBlank @Size(min = 8, max = 72) String motDePasse) {
    }

    private static final String SELECT =
            "select u.id_utilisateur, u.nom, u.email, u.actif, u.id_role, r.libelle "
                    + "from utilisateur u join role r on r.id_role = u.id_role";

    private static final RowMapper<Utilisateur> MAPPER = (rs, i) -> new Utilisateur(
            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5), rs.getString(6));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public UtilisateurController(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @GetMapping("/roles")
    public List<Role> roles() {
        return jdbc.query("select id_role, libelle from role order by id_role",
                (rs, i) -> new Role(rs.getInt(1), rs.getString(2)));
    }

    @GetMapping("/utilisateurs")
    public List<Utilisateur> lister() {
        return jdbc.query(SELECT + " order by u.nom", MAPPER);
    }

    @PostMapping("/utilisateurs")
    @ResponseStatus(HttpStatus.CREATED)
    public Utilisateur creer(@Valid @RequestBody CreationRequest req) {
        Integer id = jdbc.queryForObject(
                "insert into utilisateur (nom, email, mot_de_passe_hache, id_role) values (?, ?, ?, ?) "
                        + "returning id_utilisateur",
                Integer.class,
                req.nom().trim(), req.email().trim().toLowerCase(), encoder.encode(req.motDePasse()), req.idRole());
        return jdbc.queryForObject(SELECT + " where u.id_utilisateur = ?", MAPPER, id);
    }

    @PutMapping("/utilisateurs/{id}")
    public Utilisateur modifier(@PathVariable int id, @Valid @RequestBody ModificationRequest req,
                                @AuthenticationPrincipal AuthUser courant) {
        List<Utilisateur> existants = jdbc.query(SELECT + " where u.id_utilisateur = ?", MAPPER, id);
        if (existants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }
        // Évite de se verrouiller soi-même hors de l'application
        if (id == courant.id() && (!req.actif() || req.idRole() != existants.get(0).idRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous ne pouvez ni désactiver votre propre compte ni changer votre propre rôle");
        }
        jdbc.update("update utilisateur set nom = ?, id_role = ?, actif = ? where id_utilisateur = ?",
                req.nom().trim(), req.idRole(), req.actif(), id);
        return jdbc.queryForObject(SELECT + " where u.id_utilisateur = ?", MAPPER, id);
    }

    @PutMapping("/utilisateurs/{id}/mot-de-passe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changerMotDePasse(@PathVariable int id, @Valid @RequestBody MotDePasseRequest req) {
        int modifies = jdbc.update("update utilisateur set mot_de_passe_hache = ? where id_utilisateur = ?",
                encoder.encode(req.motDePasse()), id);
        if (modifies == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }
    }
}
