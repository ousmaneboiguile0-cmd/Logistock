package com.logistock.stock;

import com.logistock.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * F3 : mouvements de stock (entrées et sorties), horodatés et rattachés à leur auteur.
 * La mise à jour du stock et le refus d'un stock négatif sont garantis par le trigger SQL.
 */
@RestController
@RequestMapping("/api/mouvements")
@PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
public class MouvementController {

    public record Mouvement(int id, LocalDateTime dateHeure, String type, int quantite, int idProduit,
                            String reference, String designation, String auteur, Integer idCommande) {
    }

    public record MouvementRequest(
            @NotNull Integer idProduit,
            @NotBlank @Pattern(regexp = "ENTREE|SORTIE", message = "doit valoir ENTREE ou SORTIE") String type,
            @NotNull @Min(1) Integer quantite) {
    }

    public record MouvementResultat(int idMouvement, int quantiteStock) {
    }

    private static final String SELECT =
            "select m.id_mouvement, m.date_heure, m.type, m.quantite, m.id_produit, p.reference, p.designation, "
                    + "u.nom, m.id_commande from mouvement_stock m "
                    + "join produit p on p.id_produit = m.id_produit "
                    + "join utilisateur u on u.id_utilisateur = m.id_utilisateur";

    private static final RowMapper<Mouvement> MAPPER = (rs, i) -> new Mouvement(
            rs.getInt(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3), rs.getInt(4), rs.getInt(5),
            rs.getString(6), rs.getString(7), rs.getString(8), (Integer) rs.getObject(9));

    private final JdbcTemplate jdbc;

    public MouvementController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Mouvement> historique(@RequestParam(required = false) Integer produit,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(defaultValue = "100") int limite) {
        StringBuilder sql = new StringBuilder(SELECT).append(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (produit != null) {
            sql.append(" and m.id_produit = ?");
            args.add(produit);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" and m.type = ?");
            args.add(type);
        }
        sql.append(" order by m.date_heure desc, m.id_mouvement desc limit ?");
        args.add(Math.min(Math.max(limite, 1), 500));
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MouvementResultat enregistrer(@Valid @RequestBody MouvementRequest req,
                                         @AuthenticationPrincipal AuthUser courant) {
        Integer existe = jdbc.queryForObject(
                "select count(*) from produit where id_produit = ?", Integer.class, req.idProduit());
        if (existe == null || existe == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable");
        }
        Integer id = jdbc.queryForObject(
                "insert into mouvement_stock (type, quantite, id_produit, id_utilisateur) values (?, ?, ?, ?) "
                        + "returning id_mouvement",
                Integer.class, req.type(), req.quantite(), req.idProduit(), courant.id());
        Integer stock = jdbc.queryForObject(
                "select quantite_stock from produit where id_produit = ?", Integer.class, req.idProduit());
        return new MouvementResultat(id, stock);
    }
}
