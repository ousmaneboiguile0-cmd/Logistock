package com.logistock.stock;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F6 : alertes de rupture et de stock bas (produits sous leur seuil d'alerte). */
@RestController
@RequestMapping("/api/alertes")
public class AlerteController {

    public record Alerte(int idProduit, String reference, String designation, String categorie,
                         int quantiteStock, int seuilAlerte, String etat) {
    }

    private final JdbcTemplate jdbc;

    public AlerteController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Alerte> lister() {
        return jdbc.query(
                "select id_produit, reference, designation, categorie, quantite_stock, seuil_alerte, etat "
                        + "from v_produits_en_alerte order by quantite_stock, designation",
                (rs, i) -> new Alerte(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getString(7)));
    }
}
