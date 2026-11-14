package com.logistock.ventes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** F4 : gestion des clients (CRM), réservée à l'administrateur et au commercial. */
@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL')")
public class ClientController {

    public record Client(int id, String nom, String prenom, String email, String telephone,
                         String adresse, int nbAchats) {
    }

    public record ClientRequest(
            @NotBlank @Size(max = 100) String nom,
            @Size(max = 100) String prenom,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telephone,
            @Size(max = 255) String adresse) {
    }

    private static final String SELECT =
            "select c.id_client, c.nom, c.prenom, c.email, c.telephone, c.adresse, "
                    + "(select count(*) from commande o where o.id_client = c.id_client and o.statut = 'VALIDEE') "
                    + "from client c";

    private static final RowMapper<Client> MAPPER = (rs, i) -> new Client(
            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getString(5), rs.getString(6), rs.getInt(7));

    private final JdbcTemplate jdbc;

    public ClientController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @GetMapping
    public List<Client> lister(@RequestParam(required = false) String q) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" where lower(c.nom) like ? or lower(coalesce(c.prenom,'')) like ? "
                    + "or lower(coalesce(c.email,'')) like ? or coalesce(c.telephone,'') like ?");
            String motif = "%" + q.trim().toLowerCase() + "%";
            args.add(motif);
            args.add(motif);
            args.add(motif);
            args.add("%" + q.trim() + "%");
        }
        sql.append(" order by lower(c.nom), lower(coalesce(c.prenom,''))");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @GetMapping("/{id}")
    public Client lire(@PathVariable int id) {
        List<Client> l = jdbc.query(SELECT + " where c.id_client = ?", MAPPER, id);
        if (l.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable");
        }
        return l.get(0);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Client creer(@Valid @RequestBody ClientRequest req) {
        Integer id = jdbc.queryForObject(
                "insert into client (nom, prenom, email, telephone, adresse) values (?, ?, ?, ?, ?) "
                        + "returning id_client",
                Integer.class, req.nom().trim(), vide(req.prenom()), vide(req.email()),
                vide(req.telephone()), vide(req.adresse()));
        return lire(id);
    }

    @PutMapping("/{id}")
    public Client modifier(@PathVariable int id, @Valid @RequestBody ClientRequest req) {
        int n = jdbc.update(
                "update client set nom = ?, prenom = ?, email = ?, telephone = ?, adresse = ? "
                        + "where id_client = ?",
                req.nom().trim(), vide(req.prenom()), vide(req.email()),
                vide(req.telephone()), vide(req.adresse()), id);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable");
        }
        return lire(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable int id) {
        Integer nb = jdbc.queryForObject(
                "select count(*) from commande where id_client = ?", Integer.class, id);
        if (nb != null && nb > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce client a des commandes : il ne peut pas être supprimé");
        }
        int n = jdbc.update("delete from client where id_client = ?", id);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable");
        }
    }
}
