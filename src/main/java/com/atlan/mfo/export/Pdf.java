package com.atlan.mfo.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Écrivain {@code .pdf} minimal et autonome (aucune dépendance) : un rapport tabulaire
 * sur fond blanc (imprimable), police standard Helvetica (pas d'incorporation), avec
 * titre, sous-titre et pagination. Format PDF 1.4 ; texte encodé en WinAnsi (Latin-1).
 */
public final class Pdf {

    private static final double PAGE_W = 595.28;   // A4
    private static final double PAGE_H = 841.89;
    private static final double MARGIN = 42;
    private static final double ROW_H = 17;

    /** Une table nommée (ex. « Funds », « Direct deals ») dans un rapport à sections. */
    public record Section(String heading, List<String> headers, double[] weights, List<List<String>> rows) {
    }

    private Pdf() {
    }

    /**
     * Écrit un tableau paginé (une seule section).
     *
     * @param title    titre du rapport
     * @param subtitle sous-titre (ex. date, contexte) — peut être {@code null}
     * @param headers  libellés de colonnes
     * @param weights  largeurs relatives des colonnes (même taille que {@code headers})
     * @param rows     lignes (valeurs déjà formatées en texte)
     */
    public static void writeTable(Path file, String title, String subtitle, List<String> headers,
                                  double[] weights, List<List<String>> rows, boolean landscape) throws IOException {
        writeSections(file, title, subtitle, List.of(new Section(null, headers, weights, rows)), landscape);
    }

    /**
     * Écrit un rapport à plusieurs sections (tables distinctes, ex. Fonds / Deals directs
     * — métriques non partagées, §5.2-5.4). Chaque section démarre une nouvelle page.
     */
    public static void writeSections(Path file, String title, String subtitle,
                                     List<Section> sections, boolean landscape) throws IOException {
        double pageW = landscape ? PAGE_H : PAGE_W;   // A4 pivotée en paysage
        double pageH = landscape ? PAGE_W : PAGE_H;
        double contentW = pageW - 2 * MARGIN;

        List<String> pages = new ArrayList<>();
        boolean firstSectionEver = true;
        for (Section section : sections) {
            List<String> headers = section.headers();
            double[] weights = section.weights();
            List<List<String>> rows = section.rows();
            if (weights.length != headers.size()) {
                throw new IllegalArgumentException("Section \"" + section.heading() + "\": "
                        + weights.length + " weights for " + headers.size() + " headers");
            }
            double sum = 0;
            for (double w : weights) {
                sum += w;
            }
            double[] x = new double[headers.size() + 1];
            x[0] = MARGIN;
            for (int c = 0; c < headers.size(); c++) {
                x[c + 1] = x[c] + weights[c] / sum * contentW;
            }

            int i = 0;
            boolean firstPageOfSection = true;
            while (i == 0 || i < rows.size()) {
                StringBuilder cs = new StringBuilder();
                double y = pageH - MARGIN;
                if (firstSectionEver && firstPageOfSection) {
                    text(cs, "F2", 18, MARGIN, y, title);
                    y -= 24;
                    if (subtitle != null && !subtitle.isBlank()) {
                        text(cs, "F1", 10, MARGIN, y, subtitle);
                        y -= 20;
                    } else {
                        y -= 4;
                    }
                }
                if (section.heading() != null) {
                    text(cs, "F2", 13, MARGIN, y, firstPageOfSection
                            ? section.heading() : section.heading() + " (suite)");
                    y -= 20;
                } else if (!firstPageOfSection) {
                    text(cs, "F2", 12, MARGIN, y, title + " (suite)");
                    y -= 22;
                }
                // en-tête de colonnes
                for (int c = 0; c < headers.size(); c++) {
                    text(cs, "F2", 9, x[c] + 2, y, fit(headers.get(c), x[c + 1] - x[c] - 4, 9));
                }
                y -= 6;
                line(cs, MARGIN, y, pageW - MARGIN, y);
                y -= ROW_H - 6;
                // lignes de données
                while (i < rows.size() && y > MARGIN + ROW_H) {
                    List<String> row = rows.get(i);
                    for (int c = 0; c < headers.size() && c < row.size(); c++) {
                        String v = row.get(c) == null ? "" : row.get(c);
                        text(cs, "F1", 9, x[c] + 2, y, fit(v, x[c + 1] - x[c] - 4, 9));
                    }
                    y -= ROW_H;
                    i++;
                }
                line(cs, MARGIN, y + ROW_H - 5, pageW - MARGIN, y + ROW_H - 5);
                pages.add(cs.toString());
                firstPageOfSection = false;
                firstSectionEver = false;
                if (i >= rows.size()) {
                    break;
                }
            }
        }
        writePdf(file, pages, pageW, pageH);
    }

    /* ---- Commandes de flux de contenu ---- */

    private static void text(StringBuilder cs, String font, int size, double px, double py, String s) {
        cs.append("BT /").append(font).append(' ').append(size).append(" Tf ")
                .append(fmt(px)).append(' ').append(fmt(py)).append(" Td (")
                .append(escPdf(s)).append(") Tj ET\n");
    }

    private static void line(StringBuilder cs, double x1, double y1, double x2, double y2) {
        cs.append("0.7 w 0.75 G ").append(fmt(x1)).append(' ').append(fmt(y1)).append(" m ")
                .append(fmt(x2)).append(' ').append(fmt(y2)).append(" l S\n");
    }

    /** Tronque un texte pour tenir dans une largeur (approximation Helvetica ~0.52·taille). */
    private static String fit(String s, double width, int size) {
        int max = Math.max(1, (int) (width / (size * 0.52)));
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(1, max - 3)) + "...";
    }

    /* ---- Assemblage PDF (objets + xref) ---- */

    private static void writePdf(Path file, List<String> pageContents, double pageW, double pageH) throws IOException {
        int nPages = pageContents.size();
        int catalog = 1, pagesObj = 2, fReg = 3, fBold = 4;
        int firstPage = 5;
        int firstContent = 5 + nPages;

        List<String> objects = new ArrayList<>();       // corps des objets, index = numéro-1
        for (int k = 0; k < 4 + 2 * nPages; k++) {
            objects.add("");
        }

        objects.set(catalog - 1, "<< /Type /Catalog /Pages " + pagesObj + " 0 R >>");
        StringBuilder kids = new StringBuilder("[");
        for (int i = 0; i < nPages; i++) {
            kids.append(firstPage + i).append(" 0 R ");
        }
        kids.append("]");
        objects.set(pagesObj - 1, "<< /Type /Pages /Kids " + kids + " /Count " + nPages + " >>");
        objects.set(fReg - 1, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.set(fBold - 1, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < nPages; i++) {
            objects.set(firstPage + i - 1,
                    "<< /Type /Page /Parent " + pagesObj + " 0 R /MediaBox [0 0 "
                            + fmt(pageW) + " " + fmt(pageH) + "]"
                            + " /Resources << /Font << /F1 " + fReg + " 0 R /F2 " + fBold + " 0 R >> >>"
                            + " /Contents " + (firstContent + i) + " 0 R >>");
            String stream = pageContents.get(i);
            int len = stream.getBytes(StandardCharsets.ISO_8859_1).length;
            objects.set(firstContent + i - 1,
                    "<< /Length " + len + " >>\nstream\n" + stream + "\nendstream");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        int[] offsets = new int[objects.size() + 1];
        for (int n = 1; n <= objects.size(); n++) {
            offsets[n] = out.size();
            write(out, n + " 0 obj\n" + objects.get(n - 1) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder t = new StringBuilder();
        t.append("xref\n0 ").append(objects.size() + 1).append("\n");
        t.append("0000000000 65535 f \n");
        for (int n = 1; n <= objects.size(); n++) {
            t.append(String.format(Locale.US, "%010d 00000 n \n", offsets[n]));
        }
        t.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root ").append(catalog).append(" 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        write(out, t.toString());

        Files.write(file, out.toByteArray());
    }

    private static void write(OutputStream os, String s) throws IOException {
        os.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String escPdf(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '(' || ch == ')') {
                b.append('\\').append(ch);
            } else if (ch > 255) {
                switch (ch) {                       // ponctuation Unicode → équivalent ASCII
                    case '—', '–' -> b.append('-');       // tirets cadratin / demi-cadratin
                    case '‘', '’' -> b.append('\'');      // apostrophes typographiques
                    case '“', '”' -> b.append('"');       // guillemets typographiques
                    case '…' -> b.append("...");               // points de suspension
                    default -> b.append('?');
                }
            } else if (ch >= 0x20) {
                b.append(ch);
            }
        }
        return b.toString();
    }

    private static String fmt(double v) {
        if (v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.US, "%.2f", v);
    }
}
