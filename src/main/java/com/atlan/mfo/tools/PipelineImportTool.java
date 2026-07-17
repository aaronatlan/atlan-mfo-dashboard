package com.atlan.mfo.tools;

import com.atlan.mfo.auth.UserCredentials;
import com.atlan.mfo.config.AppConfig;
import com.atlan.mfo.dao.DirectDealDao;
import com.atlan.mfo.dao.FundInvestmentDao;
import com.atlan.mfo.dao.UserDao;
import com.atlan.mfo.db.Database;
import com.atlan.mfo.model.AppUser;
import com.atlan.mfo.model.DirectDeal;
import com.atlan.mfo.model.FundInvestment;
import com.atlan.mfo.model.FundVintage;
import com.atlan.mfo.model.enums.BenchmarkStatus;
import com.atlan.mfo.model.enums.Category;
import com.atlan.mfo.model.enums.Classification.AccessRoute;
import com.atlan.mfo.model.enums.Classification.AssetClass;
import com.atlan.mfo.model.enums.Classification.SecondaryMandate;
import com.atlan.mfo.model.enums.Classification.UnderlyingStrategy;
import com.atlan.mfo.model.enums.Currency;
import com.atlan.mfo.model.enums.DealStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Import ponctuel en masse d'un pipeline existant (fonds + millésimes + deals) depuis
 * un classeur Excel, en une seule fois — les ajouts ultérieurs se font ligne par ligne
 * dans l'application. Voir {@code templates/pipeline-import-template.xlsx} pour le
 * modèle de colonnes attendu (trois feuilles : Funds, Vintages, Deals).
 *
 * <p>Validation en deux passes : la feuille entière est d'abord relue et contrôlée en
 * mémoire (aucune écriture base) ; à la moindre erreur, rien n'est inséré et la liste
 * complète des lignes en défaut est affichée. Ce n'est qu'une fois le fichier
 * entièrement valide que l'insertion a lieu, fonds par fonds puis deal par deal.
 *
 * <p>Usage : {@code scripts/import-pipeline.sh <chemin_du_fichier.xlsx> <identifiant_utilisateur>}
 */
public final class PipelineImportTool {

    private PipelineImportTool() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: PipelineImportTool <fichier.xlsx> <identifiant_utilisateur>");
            System.exit(2);
            return;
        }
        String filePath = args[0];
        String username = args[1];

        AppConfig config = AppConfig.load();
        Database.init(config);
        try {
            AppUser actor = resolveUser(username);
            System.out.println("Import attribué à : " + actor.fullName() + " (" + actor.username() + ")");

            try (var in = new FileInputStream(filePath); Workbook wb = new XSSFWorkbook(in)) {
                List<String> errors = new ArrayList<>();
                List<FundBlock> funds = readFunds(wb, errors);
                attachVintages(wb, funds, errors);
                List<DirectDeal> deals = readDeals(wb, errors);

                if (!errors.isEmpty()) {
                    System.err.println("\n=== " + errors.size() + " erreur(s) — RIEN N'A ÉTÉ IMPORTÉ ===");
                    errors.forEach(e -> System.err.println("  - " + e));
                    System.err.println("\nCorriger le fichier puis relancer l'import.");
                    System.exit(1);
                    return;
                }

                System.out.println("Fichier valide : " + funds.size() + " fonds, " + deals.size() + " deals.");
                insert(funds, deals, actor.id());
                System.out.println("\n=== Import terminé ===");
                System.out.println(funds.size() + " fonds insérés, " + deals.size() + " deals insérés.");
            }
        } catch (Exception e) {
            System.err.println("Échec de l'import : " + e.getMessage());
            System.exit(1);
        } finally {
            Database.close();
        }
    }

    private static AppUser resolveUser(String username) {
        UserCredentials creds = new UserDao().findAuthByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + username));
        return creds.user();
    }

    private static void insert(List<FundBlock> funds, List<DirectDeal> deals, long userId) {
        FundInvestmentDao fundDao = new FundInvestmentDao();
        for (FundBlock fb : funds) {
            long id = fundDao.insert(fb.fund, userId);
            System.out.println("  fonds inséré : " + fb.fund.name() + " (id=" + id + ")");
        }
        DirectDealDao dealDao = new DirectDealDao();
        for (DirectDeal d : deals) {
            long id = dealDao.insert(d, userId);
            System.out.println("  deal inséré  : " + d.name() + " (id=" + id + ")");
        }
    }

    /* ---- Feuille Funds ---- */

    private record FundBlock(FundInvestment fund, List<FundVintage> vintages) {
    }

    private static List<FundBlock> readFunds(Workbook wb, List<String> errors) {
        List<FundBlock> out = new ArrayList<>();
        Sheet sheet = wb.getSheet("Funds");
        if (sheet == null) {
            errors.add("Feuille « Funds » introuvable dans le classeur.");
            return out;
        }
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isBlankRow(row)) {
                continue;
            }
            String ctx = "Funds ligne " + (row.getRowNum() + 1);
            try {
                String name = str(row, 0);
                if (name == null) {
                    errors.add(ctx + " : Name est obligatoire.");
                    continue;
                }
                AssetClass assetClass = label(row, 1, AssetClass.class, AssetClass::label, ctx, "Asset Class", errors);
                String subStrategy = str(row, 2);
                AccessRoute accessRoute = label(row, 3, AccessRoute.class, AccessRoute::label, ctx, "Access Route", errors);
                List<SecondaryMandate> mandate = labelCsv(row, 4, SecondaryMandate.class, SecondaryMandate::label, ctx, "Secondary Mandate", errors);
                List<UnderlyingStrategy> underlying = labelCsv(row, 5, UnderlyingStrategy.class, UnderlyingStrategy::label, ctx, "Underlying Strategy", errors);
                DealStatus status = label(row, 6, DealStatus.class, DealStatus::label, ctx, "Status", errors);
                BenchmarkStatus benchmark = str(row, 7) == null ? null
                        : label(row, 7, BenchmarkStatus.class, BenchmarkStatus::label, ctx, "Vs. Benchmark", errors);
                String geography = str(row, 8);
                Double commitment = num(row, 9);
                String currency = Currency.fromCode(str(row, 10)).code();
                LocalDate firstClose = date(row, 11);
                LocalDate finalClose = date(row, 12);
                String contactName = str(row, 13);
                String contactEmail = str(row, 14);
                String contactPhone = str(row, 15);
                String nextSteps = str(row, 16);
                String comments = str(row, 17);

                if (status == null) {
                    continue; // erreur déjà ajoutée par label(...)
                }
                FundInvestment fund = new FundInvestment(
                        0, legacyCategory(assetClass), name, nextSteps, status, benchmark, geography,
                        assetClass == null ? null : assetClass.name(),
                        commitment, List.of(), firstClose, finalClose, comments,
                        0, null, null, contactName, contactEmail, contactPhone, currency,
                        subStrategy, accessRoute == null ? null : accessRoute.name(),
                        csvOf(mandate), csvOf(underlying));
                out.add(new FundBlock(fund, new ArrayList<>()));
            } catch (Exception e) {
                errors.add(ctx + " : " + e.getMessage());
            }
        }
        return out;
    }

    /* ---- Feuille Vintages (rattachées par nom de fonds) ---- */

    private static void attachVintages(Workbook wb, List<FundBlock> funds, List<String> errors) {
        Sheet sheet = wb.getSheet("Vintages");
        if (sheet == null) {
            return; // feuille optionnelle : un fonds peut n'avoir aucun millésime
        }
        Map<String, FundBlock> byName = new LinkedHashMap<>();
        for (FundBlock fb : funds) {
            byName.put(fb.fund.name(), fb);
        }
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isBlankRow(row)) {
                continue;
            }
            String ctx = "Vintages ligne " + (row.getRowNum() + 1);
            String fundName = str(row, 0);
            if (fundName == null) {
                errors.add(ctx + " : Fund Name est obligatoire.");
                continue;
            }
            FundBlock fb = byName.get(fundName);
            if (fb == null) {
                errors.add(ctx + " : aucun fonds nommé « " + fundName + " » dans la feuille Funds.");
                continue;
            }
            Double yearD = num(row, 1);
            if (yearD == null) {
                errors.add(ctx + " : Vintage Year est obligatoire.");
                continue;
            }
            fb.vintages.add(new FundVintage(0, 0, yearD.intValue(),
                    num(row, 2), num(row, 3), num(row, 4), num(row, 5)));
        }
        // Reconstruit chaque FundInvestment avec ses millésimes (record immuable).
        for (int i = 0; i < funds.size(); i++) {
            FundBlock fb = funds.get(i);
            if (!fb.vintages.isEmpty()) {
                funds.set(i, new FundBlock(withVintages(fb.fund, fb.vintages), fb.vintages));
            }
        }
    }

    private static FundInvestment withVintages(FundInvestment f, List<FundVintage> vintages) {
        return new FundInvestment(f.id(), f.category(), f.name(), f.nextSteps(), f.status(), f.vsBenchmark(),
                f.geography(), f.assetClass(), f.commitment(), vintages, f.firstClose(), f.finalClose(),
                f.comments(), f.version(), f.updatedAt(), f.updatedBy(), f.contactName(), f.contactEmail(),
                f.contactPhone(), f.currency(), f.subStrategy(), f.accessRoute(), f.secondaryMandate(),
                f.underlyingStrategy());
    }

    /* ---- Feuille Deals ---- */

    private static List<DirectDeal> readDeals(Workbook wb, List<String> errors) {
        List<DirectDeal> out = new ArrayList<>();
        Sheet sheet = wb.getSheet("Deals");
        if (sheet == null) {
            return out; // feuille optionnelle
        }
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isBlankRow(row)) {
                continue;
            }
            String ctx = "Deals ligne " + (row.getRowNum() + 1);
            try {
                String name = str(row, 0);
                if (name == null) {
                    errors.add(ctx + " : Name est obligatoire.");
                    continue;
                }
                AssetClass assetClass = label(row, 1, AssetClass.class, AssetClass::label, ctx, "Asset Class", errors);
                String subStrategy = str(row, 2);
                AccessRoute accessRoute = label(row, 3, AccessRoute.class, AccessRoute::label, ctx, "Access Route", errors);
                DealStatus status = label(row, 4, DealStatus.class, DealStatus::label, ctx, "Status", errors);
                BenchmarkStatus benchmark = str(row, 5) == null ? null
                        : label(row, 5, BenchmarkStatus.class, BenchmarkStatus::label, ctx, "Vs. Benchmark", errors);
                String industry = str(row, 6);
                String gp = str(row, 7);
                String geography = str(row, 8);
                String invType = str(row, 9);
                Double commitment = num(row, 10);
                String currency = Currency.fromCode(str(row, 11)).code();
                Double revenue = num(row, 12);
                Double cagrPct = num(row, 13);
                Double ebitda = num(row, 14);
                Double ebitdaGrPct = num(row, 15);
                Double ebitdaMgnPct = num(row, 16);
                Double fcf = num(row, 17);
                Double fcfConvPct = num(row, 18);
                Double ev = num(row, 19);
                Double entryMult = num(row, 20);
                String peersMult = str(row, 21);
                Double exitVal = num(row, 22);
                Double expIrrPct = num(row, 23);
                Double expMoic = num(row, 24);
                LocalDate dealDeadline = date(row, 25);
                LocalDate targetExit = date(row, 26);
                String contactName = str(row, 27);
                String contactEmail = str(row, 28);
                String contactPhone = str(row, 29);
                String nextSteps = str(row, 30);
                String comments = str(row, 31);

                if (status == null) {
                    continue;
                }
                out.add(new DirectDeal(0, name, nextSteps, status, benchmark, industry, gp, geography, invType,
                        commitment, revenue, cagrPct, ebitda, ebitdaGrPct, ebitdaMgnPct, fcf, fcfConvPct, ev,
                        entryMult, peersMult, exitVal, expIrrPct, expMoic, dealDeadline, targetExit, comments,
                        0, null, null, contactName, contactEmail, contactPhone, currency,
                        assetClass == null ? null : assetClass.name(), subStrategy,
                        accessRoute == null ? null : accessRoute.name(), null, null));
            } catch (Exception e) {
                errors.add(ctx + " : " + e.getMessage());
            }
        }
        return out;
    }

    private static String csvOf(List<? extends Enum<?>> values) {
        if (values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Enum<?> v : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v.name());
        }
        return sb.toString();
    }

    /* ---- Repli legacy : Category dérivée de la classe d'actifs (colonne DB encore NOT NULL) ---- */

    private static Category legacyCategory(AssetClass assetClass) {
        if (assetClass == null) {
            return Category.BUYOUT_GROWTH_VC;
        }
        return switch (assetClass) {
            case PRIVATE_CREDIT -> Category.PRIVATE_CREDIT;
            case SECONDARIES -> Category.SECONDARIES;
            default -> Category.BUYOUT_GROWTH_VC;
        };
    }

    /* ---- Lecture de cellules ---- */

    private static boolean isBlankRow(Row row) {
        for (Cell c : row) {
            if (c.getCellType() != CellType.BLANK && str(c) != null) {
                return false;
            }
        }
        return true;
    }

    private static String str(Row row, int col) {
        Cell c = row.getCell(col);
        return c == null ? null : str(c);
    }

    private static String str(Cell c) {
        String s = switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> String.valueOf(c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> null;
        };
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Double num(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC) {
            return c.getNumericCellValue();
        }
        String s = str(c);
        if (s == null) {
            return null;
        }
        boolean isPercentText = s.contains("%");
        double v = Double.parseDouble(s.replace(",", ".").replace("%", "").trim());
        return isPercentText ? v / 100.0 : v;
    }

    private static final java.time.format.DateTimeFormatter[] DATE_FORMATS = {
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"),
    };

    private static LocalDate date(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        String s = str(c);
        if (s == null) {
            return null;
        }
        for (java.time.format.DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (java.time.format.DateTimeParseException ignored) {
                // essaie le format suivant
            }
        }
        throw new IllegalArgumentException("date « " + s + " » non reconnue (attendu JJ/MM/AAAA)");
    }

    /** Résout une cellule texte vers une valeur d'enum via son libellé (insensible à la casse). */
    private static <E extends Enum<E>> E label(Row row, int col, Class<E> type,
                                               java.util.function.Function<E, String> labelFn,
                                               String ctx, String colName, List<String> errors) {
        String raw = str(row, col);
        if (raw == null) {
            errors.add(ctx + " : " + colName + " est obligatoire.");
            return null;
        }
        for (E v : type.getEnumConstants()) {
            if (labelFn.apply(v).equalsIgnoreCase(raw)) {
                return v;
            }
        }
        String allowed = String.join(", ", Arrays.stream(type.getEnumConstants())
                .map(labelFn).toArray(String[]::new));
        errors.add(ctx + " : " + colName + " « " + raw + " » non reconnu. Valeurs attendues : " + allowed + ".");
        return null;
    }

    /** CSV de libellés (ex. « LP-led, Direct ») → liste d'enums ; vide si cellule vide. */
    private static <E extends Enum<E>> List<E> labelCsv(Row row, int col, Class<E> type,
                                                        java.util.function.Function<E, String> labelFn,
                                                        String ctx, String colName, List<String> errors) {
        String raw = str(row, col);
        if (raw == null) {
            return List.of();
        }
        List<E> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            boolean found = false;
            for (E v : type.getEnumConstants()) {
                if (labelFn.apply(v).equalsIgnoreCase(p)) {
                    out.add(v);
                    found = true;
                    break;
                }
            }
            if (!found) {
                String allowed = String.join(", ", Arrays.stream(type.getEnumConstants())
                        .map(labelFn).toArray(String[]::new));
                errors.add(ctx + " : " + colName + " « " + p + " » non reconnu. Valeurs attendues : " + allowed + ".");
            }
        }
        return out;
    }
}
