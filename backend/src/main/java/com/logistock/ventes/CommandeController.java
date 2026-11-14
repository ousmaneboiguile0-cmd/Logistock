package com.logistock.ventes;

import com.logistock.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
 * F5 : ventes. Une vente est une commande validée.
 * Cycle : BROUILLON -> VALIDEE (sorties de stock + facture, faits par la fonction SQL valider_commande)
 * ou BROUILLON -> ANNULEE. Les prix sont toujours lus en base, jamais fournis par le client.
 */
@RestController
@RequestMapping("/api/commandes")
@PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL')")
public class CommandeController {

    public record Commande(int id, LocalDateTime dateCommande, String statut, BigDecimal total,
                           int idClient, String client, String auteur, Integer idFacture,
                           String numeroFacture) {
    }

    public record Ligne(int idProduit, String reference, String designation, int quantite,
                        BigDecimal prixUnitaire, BigDecimal sousTotal, int stockDisponible) {
    }

    public record CommandeDetail(Commande commande, List<Ligne> lignes) {
    }

    public record LigneRequest(@NotNull Integer idProduit, @NotNull @Min(1) Integer quantite) {
    }

    public record CommandeRequest(@NotNull Integer idClient, @NotEmpty @Valid List<LigneRequest> lignes) {
    }

    public record ValidationResultat(int idFacture, String numero) {
    }

    private static final String SELECT =
            "select o.id_commande, o.date_commande, o.statut, o.total, o.id_client, "
                    + "trim(c.nom || ' ' || coalesce(c.prenom, '')), u.nom, f.id_facture, f.numero "
                    + "from commande o "
                    + "join client c on c.id_client = o.id_client "
                    + "join utilisateur u on u.id_utilisateur = o.id_utilisateur "
                    + "left join facture f on f.id_commande = o.id_commande";

    private static final RowMapper<Commande> MAPPER = (rs, i) -> new Commande(
            rs.getInt(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3), rs.getBigDecimal(4),
            rs.getInt(5), rs.getString(6), rs.getString(7), (Integer) rs.getObject(8), rs.getString(9));

    private static final RowMapper<Ligne> LIGNE_MAPPER = (rs, i) -> new Ligne(
            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5),
            rs.getBigDecimal(6), rs.getInt(7));

    private final JdbcTemplate jdbc;
    private final FactureService factures;

    public CommandeController(JdbcTemplate jdbc, FactureService factures) {
        this.jdbc = jdbc;
        this.factures = factures;
    }

    @GetMapping
    public List<Commande> lister(@RequestParam(required = false) String statut,
                                 @RequestParam(required = false) Integer client) {
        StringBuilder sql = new StringBuilder(SELECT).append(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (statut != null && !statut.isBlank()) {
            sql.append(" and o.statut = ?");
            args.add(statut);
        }
        if (client != null) {
            sql.append(" and o.id_client = ?");
            args.add(client);
        }
        sql.append(" order by o.date_commande desc, o.id_commande desc limit 200");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @GetMapping("/{id}")
    public CommandeDetail lire(@PathVariable int id) {
        List<Commande> l = jdbc.query(SELECT + " where o.id_commande = ?", MAPPER, id);
        if (l.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable");
        }
        List<Ligne> lignes = jdbc.query(
                "select l.id_produit, p.reference, p.designation, l.quantite, l.prix_unitaire, "
                        + "l.quantite * l.prix_unitaire, p.quantite_stock "
                        + "from ligne_commande l join produit p on p.id_produit = l.id_produit "
                        + "where l.id_commande = ? order by p.designation",
                LIGNE_MAPPER, id);
        return new CommandeDetail(l.get(0), lignes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CommandeDetail creer(@Valid @RequestBody CommandeRequest req,
                                @AuthenticationPrincipal AuthUser courant) {
        verifierClient(req.idClient());
        Integer id = jdbc.queryForObject(
                "insert into commande (id_client, id_utilisateur) values (?, ?) returning id_commande",
                Integer.class, req.idClient(), courant.id());
        remplir(id, req.lignes());
        return lire(id);
    }

    @PutMapping("/{id}")
    @Transactional
    public CommandeDetail modifier(@PathVariable int id, @Valid @RequestBody CommandeRequest req) {
        verrouillerBrouillon(id);
        verifierClient(req.idClient());
        jdbc.update("update commande set id_client = ? where id_commande = ?", req.idClient(), id);
        jdbc.update("delete from ligne_commande where id_commande = ?", id);
        remplir(id, req.lignes());
        return lire(id);
    }

    @PostMapping("/{id}/valider")
    @Transactional
    public ValidationResultat valider(@PathVariable int id, @AuthenticationPrincipal AuthUser courant) {
        Integer idFacture = jdbc.queryForObject(
                "select valider_commande(?, ?)", Integer.class, id, courant.id());
        String numero = jdbc.queryForObject(
                "select numero from facture where id_facture = ?", String.class, idFacture);
        return new ValidationResultat(idFacture, numero);
    }

    @PostMapping("/{id}/annuler")
    @Transactional
    public CommandeDetail annuler(@PathVariable int id) {
        verrouillerBrouillon(id);
        jdbc.update("update commande set statut = 'ANNULEE' where id_commande = ?", id);
        return lire(id);
    }

    @GetMapping("/{id}/facture")
    public ResponseEntity<byte[]> facture(@PathVariable int id) {
        byte[] pdf = factures.generer(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"facture-" + id + ".pdf\"")
                .body(pdf);
    }

    // ---------- outils ----------

    private void verifierClient(int idClient) {
        Integer n = jdbc.queryForObject("select count(*) from client where id_client = ?", Integer.class, idClient);
        if (n == null || n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable");
        }
    }

    /** Verrouille la commande et refuse si elle n'est plus un brouillon. */
    private void verrouillerBrouillon(int id) {
        List<String> st = jdbc.queryForList(
                "select statut from commande where id_commande = ? for update", String.class, id);
        if (st.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable");
        }
        if (!"BROUILLON".equals(st.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un brouillon peut être modifié ou annulé (statut actuel : " + st.get(0) + ")");
        }
    }

    /** Insère les lignes (doublons fusionnés) avec le prix actuel du produit. */
    private void remplir(int idCommande, List<LigneRequest> lignes) {
        Map<Integer, Integer> fusion = new LinkedHashMap<>();
        for (LigneRequest l : lignes) {
            fusion.merge(l.idProduit(), l.quantite(), Integer::sum);
        }
        for (Map.Entry<Integer, Integer> e : fusion.entrySet()) {
            int n = jdbc.update(
                    "insert into ligne_commande (id_commande, id_produit, quantite, prix_unitaire) "
                            + "select ?::integer, p.id_produit, ?::integer, p.prix "
                            + "from produit p where p.id_produit = ?",
                    idCommande, e.getValue(), e.getKey());
            if (n == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable : " + e.getKey());
            }
        }
    }
}
