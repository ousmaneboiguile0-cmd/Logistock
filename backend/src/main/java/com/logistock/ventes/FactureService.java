package com.logistock.ventes;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Génère la facture PDF (OpenPDF) d'une commande validée. */
@Service
public class FactureService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color GRIS = new Color(0xEE, 0xF1, 0xF5);

    private final JdbcTemplate jdbc;

    public FactureService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Montant au format français, espace simple comme séparateur de milliers (police PDF standard). */
    static String argent(BigDecimal v) {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", s).format(v);
    }

    public byte[] generer(int idCommande) {
        List<Map<String, Object>> entete = jdbc.queryForList(
                "select f.numero, f.date_emission, f.montant, "
                        + "trim(c.nom || ' ' || coalesce(c.prenom, '')) as client, c.email, c.telephone, c.adresse "
                        + "from facture f join commande o on o.id_commande = f.id_commande "
                        + "join client c on c.id_client = o.id_client where f.id_commande = ?",
                idCommande);
        if (entete.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pas de facture : la commande n'existe pas ou n'est pas validée");
        }
        Map<String, Object> e = entete.get(0);
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "select p.reference, p.designation, l.quantite, l.prix_unitaire, l.quantite * l.prix_unitaire as sous_total "
                        + "from ligne_commande l join produit p on p.id_produit = l.id_produit "
                        + "where l.id_commande = ? order by p.designation",
                idCommande);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22);
            Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font petit = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);

            doc.add(new Paragraph("LogiStock SA", titre));
            doc.add(new Paragraph("Gestion de stocks et de ventes", petit));
            doc.add(new Paragraph(" "));

            Paragraph f = new Paragraph("FACTURE " + e.get("numero"), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            doc.add(f);
            LocalDateTime date = ((java.sql.Timestamp) e.get("date_emission")).toLocalDateTime();
            doc.add(new Paragraph("Date d'émission : " + date.format(DATE), normal));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Client", gras));
            doc.add(new Paragraph(String.valueOf(e.get("client")), normal));
            for (String cle : new String[] {"adresse", "email", "telephone"}) {
                Object v = e.get(cle);
                if (v != null) {
                    doc.add(new Paragraph(String.valueOf(v), normal));
                }
            }
            doc.add(new Paragraph(" "));

            PdfPTable t = new PdfPTable(new float[] {2f, 5f, 1.5f, 2.5f, 2.5f});
            t.setWidthPercentage(100);
            for (String h : new String[] {"Référence", "Désignation", "Qté", "Prix unitaire", "Sous-total"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, gras));
                c.setBackgroundColor(GRIS);
                c.setPadding(5);
                t.addCell(c);
            }
            for (Map<String, Object> l : lignes) {
                t.addCell(cellule(String.valueOf(l.get("reference")), normal, Element.ALIGN_LEFT));
                t.addCell(cellule(String.valueOf(l.get("designation")), normal, Element.ALIGN_LEFT));
                t.addCell(cellule(String.valueOf(l.get("quantite")), normal, Element.ALIGN_RIGHT));
                t.addCell(cellule(argent((BigDecimal) l.get("prix_unitaire")), normal, Element.ALIGN_RIGHT));
                t.addCell(cellule(argent((BigDecimal) l.get("sous_total")), normal, Element.ALIGN_RIGHT));
            }
            doc.add(t);

            Paragraph total = new Paragraph("Total : " + argent((BigDecimal) e.get("montant")),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
            total.setAlignment(Element.ALIGN_RIGHT);
            total.setSpacingBefore(12);
            doc.add(total);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de générer le PDF : " + ex.getMessage());
        }
    }

    private static PdfPCell cellule(String texte, Font police, int alignement) {
        PdfPCell c = new PdfPCell(new Phrase(texte, police));
        c.setHorizontalAlignment(alignement);
        c.setPadding(5);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(Color.LIGHT_GRAY);
        return c;
    }
}
