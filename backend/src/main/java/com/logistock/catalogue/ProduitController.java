package com.logistock.catalogue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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

/**
 * F2 : produits (créer, modifier, supprimer, rechercher, classer).
 * La quantité en stock ne se modifie jamais ici : uniquement par les mouvements de stock.
 */
@RestController
@RequestMapping("/api/produits")
public class ProduitController {

    public record Produit(int id, String reference, String designation, BigDecimal prix, int quantiteStock,
                          int seuilAlerte, int idCategorie, String categorie, boolean enAlerte) {
    }

    public record ProduitRequest(
            @NotBlank @Size(max = 30) String reference,
            @NotBlank @Size(max = 150) String designation,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal prix,
            @NotNull @Min(0) Integer seuilAlerte,
            @NotNull Integer idCategorie) {
    }

    private static final String SELECT =
            "select p.id_produit, p.reference, p.designation, p.prix, p.quantite_stock, p.seuil_alerte, "
                    + "p.id_categorie, c.nom, (p.quantite_stock < p.seuil_alerte or p.quantite_stock = 0) "
                    + "from produit p join categorie c on c.id_categorie = p.id_categorie";

    private static final RowMapper<Produit> MAPPER = (rs, i) -> new Produit(
            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getInt(5),
            rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getBoolean(9));

    private final JdbcTemplate jdbc;

    public ProduitController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Produit> lister(@RequestParam(required = false) String q,
                                @RequestParam(required = false) Integer categorie) {
        StringBuilder sql = new StringBuilder(SELECT).append(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" and (p.reference ilike ? or p.designation ilike ?)");
            String motif = "%" + q.trim() + "%";
            args.add(motif);
            args.add(motif);
        }
        if (categorie != null) {
            sql.append(" and p.id_categorie = ?");
            args.add(categorie);
        }
        sql.append(" order by p.designation");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @GetMapping("/{id}")
    public Produit lire(@PathVariable int id) {
        List<Produit> lignes = jdbc.query(SELECT + " where p.id_produit = ?", MAPPER, id);
        if (lignes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable");
        }
        return lignes.get(0);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public Produit creer(@Valid @RequestBody ProduitRequest req) {
        Integer id = jdbc.queryForObject(
                "insert into produit (reference, designation, prix, seuil_alerte, id_categorie) "
                        + "values (?, ?, ?, ?, ?) returning id_produit",
                Integer.class,
                req.reference().trim(), req.designation().trim(), req.prix(), req.seuilAlerte(), req.idCategorie());
        return lire(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public Produit modifier(@PathVariable int id, @Valid @RequestBody ProduitRequest req) {
        int modifies = jdbc.update(
                "update produit set reference = ?, designation = ?, prix = ?, seuil_alerte = ?, id_categorie = ? "
                        + "where id_produit = ?",
                req.reference().trim(), req.designation().trim(), req.prix(), req.seuilAlerte(), req.idCategorie(), id);
        if (modifies == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable");
        }
        return lire(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public void supprimer(@PathVariable int id) {
        Integer historique = jdbc.queryForObject(
                "select count(*) from mouvement_stock where id_produit = ?", Integer.class, id);
        if (historique != null && historique > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce produit a un historique de mouvements : il ne peut pas être supprimé");
        }
        int supprimes = jdbc.update("delete from produit where id_produit = ?", id);
        if (supprimes == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable");
        }
    }
}
