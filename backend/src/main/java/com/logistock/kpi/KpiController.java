package com.logistock.kpi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F7 : tableau de bord KPI, en lecture seule, ouvert à tous les utilisateurs connectés.
 * Seules les ventes validées comptent dans le chiffre d'affaires.
 */
@RestController
@RequestMapping("/api/kpi")
public class KpiController {

    public record JourCa(LocalDate jour, int nbVentes, BigDecimal chiffreAffaires) {
    }

    public record TopProduit(String reference, String designation, long quantiteVendue, BigDecimal chiffreAffaires) {
    }

    public record JourMouvements(LocalDate jour, long entrees, long sorties) {
    }

    public record Kpi(BigDecimal caJour, int ventesJour, BigDecimal caMois, int ventesMois,
                      BigDecimal caMoisPrecedent, BigDecimal variationMoisPct,
                      BigDecimal valeurStock, int produitsEnAlerte, int produitsEnRupture,
                      List<JourCa> caParJour, List<TopProduit> topProduits,
                      List<JourMouvements> mouvementsSemaine) {
    }

    private final JdbcTemplate jdbc;

    public KpiController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Variation en % du mois courant par rapport au précédent ; null si le mois précédent est à 0. */
    static BigDecimal variation(BigDecimal courant, BigDecimal precedent) {
        if (precedent == null || precedent.signum() == 0) {
            return null;
        }
        return courant.subtract(precedent).multiply(BigDecimal.valueOf(100))
                .divide(precedent, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal ca(String condition) {
        return jdbc.queryForObject("select coalesce(sum(total), 0) from commande where statut = 'VALIDEE' and " + condition,
                BigDecimal.class);
    }

    private int nb(String condition) {
        Integer n = jdbc.queryForObject("select count(*) from commande where statut = 'VALIDEE' and " + condition,
                Integer.class);
        return n == null ? 0 : n;
    }

    @GetMapping
    public Kpi tableauDeBord() {
        String jour = "date_commande::date = current_date";
        String mois = "date_trunc('month', date_commande) = date_trunc('month', now())";
        String precedent = "date_trunc('month', date_commande) = date_trunc('month', now()) - interval '1 month'";

        BigDecimal caMois = ca(mois);
        BigDecimal caPrec = ca(precedent);

        BigDecimal valeur = jdbc.queryForObject(
                "select coalesce(sum(prix * quantite_stock), 0) from produit", BigDecimal.class);
        Integer alertes = jdbc.queryForObject("select count(*) from v_produits_en_alerte", Integer.class);
        Integer ruptures = jdbc.queryForObject(
                "select count(*) from v_produits_en_alerte where etat = 'RUPTURE'", Integer.class);

        List<JourCa> caParJour = jdbc.query(
                "select jour, nb_ventes, chiffre_affaires from v_chiffre_affaires_jour "
                        + "where jour >= current_date - 29 order by jour",
                (rs, i) -> new JourCa(rs.getDate(1).toLocalDate(), rs.getInt(2), rs.getBigDecimal(3)));

        List<TopProduit> top = jdbc.query(
                "select reference, designation, quantite_vendue, chiffre_affaires "
                        + "from v_produits_plus_vendus limit 5",
                (rs, i) -> new TopProduit(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)));

        List<JourMouvements> semaine = jdbc.query(
                "select d::date, "
                        + "coalesce(sum(m.quantite) filter (where m.type = 'ENTREE'), 0), "
                        + "coalesce(sum(m.quantite) filter (where m.type = 'SORTIE'), 0) "
                        + "from generate_series(current_date - 6, current_date, interval '1 day') d "
                        + "left join mouvement_stock m on m.date_heure::date = d::date "
                        + "group by d order by d",
                (rs, i) -> new JourMouvements(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3)));

        return new Kpi(ca(jour), nb(jour), caMois, nb(mois), caPrec, variation(caMois, caPrec),
                valeur, alertes == null ? 0 : alertes, ruptures == null ? 0 : ruptures,
                caParJour, top, semaine);
    }
}
