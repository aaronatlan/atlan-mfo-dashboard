package com.atlan.mfo.tools;

import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.Classification.AccessRoute;
import com.atlan.mfo.model.enums.Classification.AssetClass;
import com.atlan.mfo.model.enums.Classification.SecondaryMandate;
import com.atlan.mfo.model.enums.Classification.UnderlyingStrategy;
import com.atlan.mfo.model.enums.Currency;
import com.atlan.mfo.model.enums.DealStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Génère le classeur modèle {@code templates/pipeline-import-template.xlsx} lu par
 * {@link PipelineImportTool}. À relancer (mvn compile puis exécution directe de cette
 * classe) uniquement si les colonnes attendues par l'import changent — le fichier
 * généré est ensuite versionné tel quel, aucune donnée réelle n'y figure jamais.
 */
public final class PipelineImportTemplateTool {

    private static final String OUT_PATH = "templates/pipeline-import-template.xlsx";

    private PipelineImportTemplateTool() {
    }

    public static void main(String[] args) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);

            buildInstructions(wb, header);
            buildFunds(wb, header);
            buildVintages(wb, header);
            buildDeals(wb, header);

            new java.io.File("templates").mkdirs();
            try (FileOutputStream out = new FileOutputStream(OUT_PATH)) {
                wb.write(out);
            }
            System.out.println("Modèle généré : " + OUT_PATH);
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        bold.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void header(Sheet sheet, CellStyle style, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titles[i]);
            c.setCellStyle(style);
            sheet.setColumnWidth(i, Math.max(14, titles[i].length() + 4) * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private static <E extends Enum<E>> void dropdown(Sheet sheet, int col, int firstRow, int lastRow,
                                                       Class<E> type, Function<E, String> labelFn) {
        String[] labels = Arrays.stream(type.getEnumConstants()).map(labelFn).toArray(String[]::new);
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(labels);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = dvHelper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private static void instructionLine(Sheet sheet, int rowIdx, String text) {
        sheet.createRow(rowIdx).createCell(0).setCellValue(text);
    }

    private static void buildInstructions(XSSFWorkbook wb, CellStyle header) {
        Sheet sheet = wb.createSheet("Instructions");
        sheet.setColumnWidth(0, 120 * 256);
        int r = 0;
        Row title = sheet.createRow(r++);
        Cell c = title.createCell(0);
        c.setCellValue("Modèle d'import Patrimium MFO Dashboard — à lire avant de remplir");
        c.setCellStyle(header);
        r++;
        instructionLine(sheet, r++, "Trois feuilles à remplir : Funds (fonds), Vintages (millésimes par fonds), Deals (deals directs / co-investissements).");
        instructionLine(sheet, r++, "Ne pas modifier la ligne d'en-tête (ligne 1) de chaque feuille — l'import s'appuie sur l'ordre des colonnes, pas sur leur libellé.");
        instructionLine(sheet, r++, "Une ligne = une opportunité. Laisser une cellule vide si l'information n'est pas connue (« non communiqué », exclu du scoring).");
        instructionLine(sheet, r++, "Les colonnes avec liste déroulante (Asset Class, Access Route, Status, Vs. Benchmark, Currency) n'acceptent que les valeurs proposées — cliquer sur la cellule pour voir la flèche.");
        instructionLine(sheet, r++, "Secondary Mandate et Underlying Strategy (feuille Funds, uniquement pour un Asset Class = Secondaries) : plusieurs valeurs séparées par une virgule, ex. « LP-led, GP-led ».");
        instructionLine(sheet, r++, "Feuille Vintages : Fund Name doit correspondre EXACTEMENT (mêmes majuscules/minuscules) au Name saisi dans la feuille Funds — c'est ce qui relie un millésime à son fonds.");
        instructionLine(sheet, r++, "Un fonds peut avoir zéro, un ou plusieurs millésimes (une ligne par millésime dans Vintages).");
        instructionLine(sheet, r++, "Pourcentages (Revenue CAGR, EBITDA Growth, EBITDA Margin, FCF Conversion, Expected IRR) : la cellule est pré-formatée en %, saisir simplement le nombre (ex. taper 12 affiche 12,00 %).");
        instructionLine(sheet, r++, "Dates (First Close, Final Close, Deal Deadline, Target Exit) : utiliser le sélecteur de date d'Excel ou le format JJ/MM/AAAA.");
        instructionLine(sheet, r++, "Une fois les trois feuilles remplies, envoyer ce fichier à la personne qui exécute l'import (scripts/import-pipeline.sh) — jamais par un canal non sécurisé si les données sont confidentielles.");
        instructionLine(sheet, r++, "L'import est tout ou rien : si une seule ligne contient une erreur, rien n'est enregistré et la liste complète des erreurs est renvoyée pour correction.");
    }

    private static void buildFunds(XSSFWorkbook wb, CellStyle header) {
        Sheet sheet = wb.createSheet("Funds");
        header(sheet, header, "Name", "Asset Class", "Sub-Strategy", "Access Route", "Secondary Mandate",
                "Underlying Strategy", "Status", "Vs. Benchmark", "Geography", "Commitment", "Currency",
                "First Close", "Final Close", "Contact Name", "Contact Email", "Contact Phone",
                "Next Steps", "Comments");

        dropdown(sheet, 1, 1, 500, AssetClass.class, AssetClass::label);
        dropdown(sheet, 3, 1, 500, AccessRoute.class, AccessRoute::label);
        dropdown(sheet, 6, 1, 500, DealStatus.class, DealStatus::label);
        dropdown(sheet, 7, 1, 500, BenchmarkStatus.class, BenchmarkStatus::label);
        dropdown(sheet, 10, 1, 500, Currency.class, Currency::code);

        Row example = sheet.createRow(1);
        String[] values = {"Exemple Fund IV", "Private Equity", "Buyout", "Primary fund commitment", "",
                "", "Due diligence", "Above threshold", "France", "5000000", "EUR",
                "15/01/2024", "15/01/2025", "Jane Doe", "jane.doe@example.com", "+33 1 23 45 67 89",
                "Attendre le closing final", "Ligne d'exemple — à supprimer avant l'import réel"};
        for (int i = 0; i < values.length; i++) {
            example.createCell(i).setCellValue(values[i]);
        }
    }

    private static void buildVintages(XSSFWorkbook wb, CellStyle header) {
        Sheet sheet = wb.createSheet("Vintages");
        header(sheet, header, "Fund Name", "Vintage Year", "DPI", "TVPI", "IRR", "MOIC");

        Row example = sheet.createRow(1);
        String[] values = {"Exemple Fund IV", "2020", "0.4", "1.3", "0.15", "1.4"};
        for (int i = 0; i < values.length; i++) {
            example.createCell(i).setCellValue(values[i]);
        }
    }

    private static void buildDeals(XSSFWorkbook wb, CellStyle header) {
        Sheet sheet = wb.createSheet("Deals");
        header(sheet, header, "Name", "Asset Class", "Sub-Strategy", "Access Route", "Status", "Vs. Benchmark",
                "Industry", "GP/Sponsor", "Geography", "Investment Type", "Commitment", "Currency",
                "Revenue", "Revenue CAGR (%)", "EBITDA", "EBITDA Growth (%)", "EBITDA Margin (%)",
                "FCF", "FCF Conversion (%)", "EV", "Entry Multiple", "Peer Multiples", "Exit Value",
                "Expected IRR (%)", "Expected MOIC", "Deal Deadline", "Target Exit",
                "Contact Name", "Contact Email", "Contact Phone", "Next Steps", "Comments");

        dropdown(sheet, 1, 1, 500, AssetClass.class, AssetClass::label);
        dropdown(sheet, 3, 1, 500, AccessRoute.class, AccessRoute::label);
        dropdown(sheet, 4, 1, 500, DealStatus.class, DealStatus::label);
        dropdown(sheet, 5, 1, 500, BenchmarkStatus.class, BenchmarkStatus::label);
        dropdown(sheet, 11, 1, 500, Currency.class, Currency::code);

        Row example = sheet.createRow(1);
        String[] values = {"Exemple Deal Co-invest", "Venture Capital", "Late-Stage", "Co-investment",
                "Screening", "N/A", "Software", "Acme Capital", "Émirats arabes unis", "Equity",
                "2000000", "USD", "10000000", "25%", "2000000", "30%", "20%", "1500000", "80%",
                "40000000", "8", "7-9x", "60000000", "22%", "3.1", "01/09/2024", "01/09/2029",
                "John Smith", "john.smith@example.com", "+971 4 123 4567",
                "Finaliser la data room", "Ligne d'exemple — à supprimer avant l'import réel"};
        for (int i = 0; i < values.length; i++) {
            example.createCell(i).setCellValue(values[i]);
        }
    }
}
