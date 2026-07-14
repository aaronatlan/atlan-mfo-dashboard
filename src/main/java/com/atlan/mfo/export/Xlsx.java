package com.atlan.mfo.export;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Écrivain {@code .xlsx} minimal et autonome (aucune dépendance) : un classeur à une ou
 * plusieurs feuilles, chaînes en ligne (inline strings) et nombres. Suffisant pour
 * exporter des tableaux vers Excel / Numbers / LibreOffice. Format OOXML (ECMA-376).
 */
public final class Xlsx {

    /** Une feuille : nom, en-têtes de colonnes, lignes (valeurs String, Number ou null). */
    public record Sheet(String name, List<String> headers, List<List<Object>> rows) {
    }

    private Xlsx() {
    }

    /** Écrit un classeur à une seule feuille. */
    public static void write(Path file, String sheetName, List<String> headers,
                             List<List<Object>> rows) throws IOException {
        write(file, List.of(new Sheet(sheetName, headers, rows)));
    }

    /** Écrit un classeur à plusieurs feuilles (une par élément de {@code sheets}). */
    public static void write(Path file, List<Sheet> sheets) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            entry(z, "[Content_Types].xml", contentTypes(sheets.size()));
            entry(z, "_rels/.rels", RELS);
            entry(z, "xl/workbook.xml", workbook(sheets));
            entry(z, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
            for (int i = 0; i < sheets.size(); i++) {
                entry(z, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(sheets.get(i)));
            }
        }
    }

    /* ---- Parties fixes / générées ---- */

    private static final String RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>""";

    private static String contentTypes(int nSheets) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= nSheets; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\""
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String workbookRels(int nSheets) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= nSheets; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
                            + " Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        return sb.append("</Relationships>").toString();
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(esc(sheets.get(i).name())).append("\" sheetId=\"")
                    .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private static String sheet(Sheet sheet) {
        List<String> headers = sheet.headers();
        List<List<Object>> rows = sheet.rows();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int r = 1;
        sb.append("<row r=\"").append(r).append("\">");
        for (int c = 0; c < headers.size(); c++) {
            sb.append(strCell(ref(c, r), headers.get(c)));
        }
        sb.append("</row>");
        for (List<Object> row : rows) {
            r++;
            sb.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < row.size(); c++) {
                Object v = row.get(c);
                if (v == null) {
                    continue;
                }
                if (v instanceof Number n) {
                    sb.append("<c r=\"").append(ref(c, r)).append("\"><v>").append(num(n)).append("</v></c>");
                } else {
                    sb.append(strCell(ref(c, r), v.toString()));
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String strCell(String ref, String text) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + esc(text) + "</t></is></c>";
    }

    /** Nombre en notation décimale simple (jamais scientifique, qu'Excel ne lit pas dans <v>). */
    private static String num(Number n) {
        double d = n.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return String.format(Locale.US, "%.4f", d);
    }

    /** Référence de cellule (ex. colonne 0, ligne 1 → « A1 »). */
    private static String ref(int col, int row) {
        StringBuilder s = new StringBuilder();
        int c = col;
        do {
            s.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        } while (c >= 0);
        return s.append(row).toString();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&apos;");
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n') {
                        b.append(ch);
                    }
                }
            }
        }
        return b.toString();
    }

    private static void entry(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        OutputStream os = z;
        os.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
