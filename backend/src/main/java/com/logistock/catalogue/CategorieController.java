package com.logistock.catalogue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** F2 : catégories de produits. Lecture pour tous, écriture pour Administrateur et Gestionnaire. */
@RestController
@RequestMapping("/api/categories")
public class CategorieController {

    public record Categorie(int id, String nom, int nbProduits) {
    }

    public record CategorieRequest(@NotBlank @Size(max = 80) String nom) {
    }

    private static final String SELECT =
            "select c.id_categorie, c.nom, count(p.id_produit) from categorie c "
                    + "left join produit p on p.id_categorie = c.id_categorie ";

    private static final RowMapper<Categorie> MAPPER =
            (rs, i) -> new Categorie(rs.getInt(1), rs.getString(2), rs.getInt(3));

    private final JdbcTemplate jdbc;

    public CategorieController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Categorie> lister() {
        return jdbc.query(SELECT + "group by c.id_categorie, c.nom order by c.nom", MAPPER);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public Categorie creer(@Valid @RequestBody CategorieRequest req) {
        Integer id = jdbc.queryForObject(
                "insert into categorie (nom) values (?) returning id_categorie", Integer.class, req.nom().trim());
        return lire(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public Categorie modifier(@PathVariable int id, @Valid @RequestBody CategorieRequest req) {
        int modifies = jdbc.update("update categorie set nom = ? where id_categorie = ?", req.nom().trim(), id);
        if (modifies == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie introuvable");
        }
        return lire(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public void supprimer(@PathVariable int id) {
        Integer nb = jdbc.queryForObject("select count(*) from produit where id_categorie = ?", Integer.class, id);
        if (nb != null && nb > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette catégorie contient encore " + nb + " produit(s) : déplacez-les avant de la supprimer");
        }
        int supprimes = jdbc.update("delete from categorie where id_categorie = ?", id);
        if (supprimes == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie introuvable");
        }
    }

    private Categorie lire(int id) {
        return jdbc.queryForObject(SELECT + "where c.id_categorie = ? group by c.id_categorie, c.nom", MAPPER, id);
    }
}
