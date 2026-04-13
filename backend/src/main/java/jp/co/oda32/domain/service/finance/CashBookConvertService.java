package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfClientMapping;
import jp.co.oda32.domain.model.finance.MMfJournalRule;
import jp.co.oda32.domain.repository.finance.MMfClientMappingRepository;
import jp.co.oda32.domain.repository.finance.MMfJournalRuleRepository;
import jp.co.oda32.dto.finance.cashbook.CashBookPreviewResponse;
import jp.co.oda32.dto.finance.cashbook.CashBookPreviewRow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashBookConvertService {

    private final MMfJournalRuleRepository ruleRepository;
    private final MMfClientMappingRepository mappingRepository;
    private final jp.co.oda32.domain.repository.finance.TCashbookImportHistoryRepository historyRepository;

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_SHEETS = 10;
    private static final int MAX_DATA_ROWS = 10000;
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024; // 20MB
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;

    private final Map<String, CachedUpload> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void initPoiSecurity() {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
    }

    // ---- Public API ----

    public CashBookPreviewResponse preview(MultipartFile file) throws IOException {
        validateFile(file);

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                throw new IllegalArgumentException("シート数上限を超過しています: " + workbook.getNumberOfSheets());
            }
            Sheet sheet = selectSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("現金出納帳シート（記入/現金出納帳）が見つかりません");
            }
            List<ParsedRow> parsed = parseSheet(sheet);
            if (parsed.size() > MAX_DATA_ROWS) {
                throw new IllegalArgumentException("データ行数上限を超過しています: " + parsed.size());
            }

            // Sheet2から期間ラベルを抽出（例: "2026   1/21 ～  2/20"）
            String periodLabel = extractPeriodLabel(workbook);

            enforceCacheLimit();
            String uploadId = UUID.randomUUID().toString();
            CachedUpload cached = CachedUpload.builder()
                    .rows(parsed)
                    .fileName(sanitize(file.getOriginalFilename()))
                    .periodLabel(periodLabel)
                    .expiresAt(System.currentTimeMillis() + CACHE_TTL_MILLIS)
                    .build();
            cache.put(uploadId, cached);

            return buildPreview(uploadId, cached);
        }
    }

    private String extractPeriodLabel(Workbook workbook) {
        // Sheet2のR4に期間ラベルがある（例: "2026   1/21 ～  2/20"）
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if ("Sheet2".equals(name) || (name != null && name.contains("Sheet"))) {
                Sheet s = workbook.getSheetAt(i);
                for (int r = 0; r <= Math.min(s.getLastRowNum(), 10); r++) {
                    Row row = s.getRow(r);
                    if (row == null) continue;
                    for (int c = 0; c < 5; c++) {
                        String v = readStringCell(row.getCell(c));
                        if (v != null && v.contains("～")) {
                            return v.strip().replaceAll("\\s+", " ");
                        }
                    }
                }
            }
        }
        return null;
    }

    public CashBookPreviewResponse rePreview(String uploadId) {
        CachedUpload cached = getCached(uploadId);
        return buildPreview(uploadId, cached);
    }

    /**
     * エラー0件ならCSVバイト列を返す。エラーが残存する場合は IllegalStateException。
     */
    public byte[] convert(String uploadId) {
        CachedUpload cached = getCached(uploadId);
        CashBookPreviewResponse preview = buildPreview(uploadId, cached);
        if (preview.getErrorCount() > 0) {
            throw new IllegalStateException("エラーが残存しています。マッピングを追加してください: " + preview.getErrorCount() + "件");
        }
        byte[] csvBytes = toCsvBytes(preview.getRows());

        saveHistory(cached, preview, new String(csvBytes, StandardCharsets.UTF_8));

        return csvBytes;
    }

    private void saveHistory(CachedUpload cached, CashBookPreviewResponse preview, String csvContent) {
        try {
            String period = cached.getPeriodLabel();
            if (period == null || period.isEmpty()) {
                period = cached.getFileName();
            }

            int totalIncome = cached.getRows().stream().mapToInt(ParsedRow::getIncome).sum();
            int totalPayment = cached.getRows().stream().mapToInt(ParsedRow::getPayment).sum();

            // 同じ期間の場合はUPDATE（再処理対応）
            var existing = historyRepository.findByPeriodLabel(period);
            var history = existing.orElse(
                    jp.co.oda32.domain.model.finance.TCashbookImportHistory.builder()
                            .periodLabel(period)
                            .build()
            );
            history.setFileName(cached.getFileName());
            history.setProcessedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            history.setRowCount(preview.getTotalRows());
            history.setTotalIncome(totalIncome);
            history.setTotalPayment(totalPayment);
            history.setCsvContent(csvContent);
            historyRepository.save(history);

            log.info("現金出納帳処理履歴を保存: period={}, rows={}", period, preview.getTotalRows());
        } catch (Exception e) {
            log.error("処理履歴の保存に失敗（CSV出力は正常完了）", e);
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    void cleanExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
    }

    // ---- Internal ----

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("ファイルが空です");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("ファイルサイズ上限(20MB)を超過しています");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("xlsxファイルのみ対応しています");
        }
        String contentType = file.getContentType();
        if (contentType != null && !XLSX_CONTENT_TYPE.equals(contentType) && !contentType.isBlank()) {
            if (!"application/octet-stream".equals(contentType)) {
                throw new IllegalArgumentException("xlsxファイルのみ対応しています");
            }
        }
    }

    private void enforceCacheLimit() {
        if (cache.size() < MAX_CACHE_ENTRIES) return;
        // 期限切れ優先削除、それでも超過するなら最古エントリを削除
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
        while (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().getExpiresAt()))
                    .ifPresent(e -> cache.remove(e.getKey()));
        }
    }

    private static String sanitize(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\n]", "_");
    }

    Sheet selectSheet(Workbook workbook) {
        Sheet exactKinyu = null;
        Sheet partialKinyu = null;
        Sheet partialCashBook = null;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name == null) continue;
            if (name.contains("MF")) continue;
            if ("記入".equals(name) && exactKinyu == null) exactKinyu = workbook.getSheetAt(i);
            else if (name.contains("記入") && partialKinyu == null) partialKinyu = workbook.getSheetAt(i);
            else if (name.contains("現金出納帳") && partialCashBook == null) partialCashBook = workbook.getSheetAt(i);
        }
        if (exactKinyu != null) return exactKinyu;
        if (partialKinyu != null) return partialKinyu;
        if (partialCashBook != null) return partialCashBook;
        // フォールバック: 先頭シート（MFを除く）
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name != null && !name.contains("MF")) return workbook.getSheetAt(i);
        }
        return null;
    }

    /**
     * Python版の前処理ロジックを再現して ParsedRow リストを作成する。
     * - A1: 年
     * - 行3以降: データ
     * - 空白は前行補完
     * - 〃 は前行D列値で置換
     * - セブンイレブンは次行D列を結合
     * - A列 >= 2000 は年更新
     * - 月 < 前月 は年++
     */
    List<ParsedRow> parseSheet(Sheet sheet) {
        int year = extractYear(sheet);

        // 左半分（列0-7）を解析
        List<ParsedRow> leftRows = parseHalf(sheet, year, 0, 1, 2, 3, 4, 7);

        // 右半分（列14-21）が存在するか確認
        boolean hasRightHalf = false;
        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            Cell rightYearCell = headerRow.getCell(14);
            if (rightYearCell == null) rightYearCell = headerRow.getCell(15);
            Integer rightYear = readIntegerCell(rightYearCell);
            String rightStr = readStringCell(rightYearCell);
            if (rightYear != null && rightYear >= 2000) {
                hasRightHalf = true;
            } else if (rightStr != null && (rightStr.contains("年") || rightStr.contains("月"))) {
                hasRightHalf = true;
            }
            // 列15に「年」or 列16に「摘要」があれば右半分あり
            if (!hasRightHalf) {
                for (int c = 14; c <= 17; c++) {
                    String v = readStringCell(headerRow.getCell(c));
                    if (v != null && (v.contains("年") || v.contains("摘") || v.contains("収入"))) {
                        hasRightHalf = true;
                        break;
                    }
                }
            }
        }

        if (hasRightHalf) {
            // 右半分の年を取得（R1のcol14 or col15がない場合は左半分と同じ年）
            int rightYear = year;
            if (headerRow != null) {
                Integer ry = readIntegerCell(headerRow.getCell(14));
                if (ry == null) ry = readIntegerCell(headerRow.getCell(15));
                if (ry != null && ry >= 2000) rightYear = ry;
            }
            // 右半分: 月=col14, 日=col15, C=col16, D=col17, 収入=col18, 支払=col21
            List<ParsedRow> rightRows = parseHalf(sheet, rightYear, 14, 15, 16, 17, 18, 21);
            leftRows.addAll(rightRows);
        }

        // 日付順にソート
        leftRows.sort(Comparator.comparingInt(ParsedRow::getYear)
                .thenComparingInt(ParsedRow::getMonth)
                .thenComparingInt(ParsedRow::getDay)
                .thenComparingInt(ParsedRow::getExcelRowIndex));

        return leftRows;
    }

    private List<ParsedRow> parseHalf(Sheet sheet, int year,
                                       int colMonth, int colDay, int colC, int colD,
                                       int colIncome, int colPayment) {
        List<ParsedRow> out = new ArrayList<>();
        Integer prevMonth = null;
        Integer prevDay = null;
        String prevDescC = null;
        String prevDescD = null;

        int last = sheet.getLastRowNum();
        for (int i = 2; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell a = row.getCell(colMonth);
            Integer monthVal = readIntegerCell(a);
            String aStr = readStringCell(a);

            Integer month;
            if (aStr == null || aStr.isEmpty()) {
                month = prevMonth;
            } else {
                if (monthVal != null && monthVal >= 2000) {
                    year = monthVal;
                    prevMonth = null;
                    continue;
                }
                if (monthVal == null) {
                    month = prevMonth;
                } else {
                    if (prevMonth != null && monthVal < prevMonth) {
                        year++;
                    }
                    prevMonth = monthVal;
                    month = monthVal;
                }
            }

            Cell b = row.getCell(colDay);
            Integer dayVal = readIntegerCell(b);
            String bStr = readStringCell(b);
            Integer day;
            if (bStr == null || bStr.isEmpty()) {
                day = prevDay;
            } else {
                day = dayVal;
                if (day != null) prevDay = day;
            }

            String origC = readStringCell(row.getCell(colC));
            String origD = readStringCell(row.getCell(colD));
            origC = origC == null ? "" : origC.strip();
            origD = origD == null ? "" : origD.strip();
            if (origC.isEmpty() && origD.isEmpty()) {
                continue;
            }

            String descC = origC.isEmpty() ? prevDescC : origC;
            String descD = origD.isEmpty() ? prevDescD : origD;

            if ("セブンイレブン".equals(descD) && i + 1 <= last) {
                Row next = sheet.getRow(i + 1);
                if (next != null) {
                    String nextD = readStringCell(next.getCell(colD));
                    if (nextD != null && !nextD.strip().isEmpty()) {
                        descD = "セブンイレブン " + nextD.strip();
                    }
                }
            }

            if (descD != null && descD.contains("〃") && prevDescD != null) {
                descD = descD.replace("〃", prevDescD);
            }

            prevDescC = descC;
            prevDescD = descD;

            if ((descC == null || descC.isEmpty()) && (descD == null || descD.isEmpty())) continue;
            if (month == null || day == null) continue;

            int income = readIntCellOrZero(row.getCell(colIncome));
            int payment = readIntCellOrZero(row.getCell(colPayment));
            if (income == 0 && payment == 0) continue;

            String descCNorm = normalize(descC);

            out.add(ParsedRow.builder()
                    .excelRowIndex(i + 1)
                    .year(year)
                    .month(month)
                    .day(day)
                    .descC(descC == null ? "" : descC)
                    .descCNorm(descCNorm)
                    .descD(descD == null ? "" : descD)
                    .income(income)
                    .payment(payment)
                    .build());
        }
        return out;
    }

    int extractYear(Sheet sheet) {
        Row r0 = sheet.getRow(0);
        if (r0 == null) return LocalDateTime.now().getYear();
        Cell c = r0.getCell(0);
        Integer v = readIntegerCell(c);
        if (v != null && v >= 2000 && v <= 2999) return v;
        String s = readStringCell(c);
        if (s != null) {
            try {
                int y = Integer.parseInt(s.strip());
                if (y >= 2000 && y <= 2999) return y;
            } catch (NumberFormatException ignored) {}
        }
        return LocalDateTime.now().getYear();
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").replaceAll("\u3000", "");
    }

    private CashBookPreviewResponse buildPreview(String uploadId, CachedUpload cached) {
        List<MMfJournalRule> rules = ruleRepository.findByDelFlgOrderByDescriptionCAscPriorityAsc("0");
        List<MMfClientMapping> mappings = mappingRepository.findByDelFlgOrderByAliasAsc("0");

        List<CashBookPreviewRow> outRows = new ArrayList<>();
        Set<String> unmapped = new LinkedHashSet<>();
        Set<String> unknownC = new LinkedHashSet<>();
        int txnNo = 1;
        int errorCount = 0;

        for (ParsedRow p : cached.getRows()) {
            MMfJournalRule rule = findRule(rules, p);
            if (rule == null) {
                outRows.add(errorRow(p, "UNKNOWN_DESCRIPTION_C", "未定義の摘要C列: '" + p.getDescC() + "'"));
                unknownC.add(p.getDescC());
                errorCount++;
                continue;
            }

            int amount = "INCOME".equals(rule.getAmountSource()) ? p.getIncome() : p.getPayment();

            // クライアントマッピング
            String creditSubAccount = rule.getCreditSubAccount();
            String creditSubTpl = rule.getCreditSubAccountTemplate();
            if (creditSubTpl != null && creditSubTpl.contains("{client}")) {
                String mapped = resolveClient(mappings, p.getDescD());
                if (mapped == null) {
                    outRows.add(errorRow(p, "UNMAPPED_CLIENT", "得意先マッピングが存在しません: " + p.getDescD()));
                    unmapped.add(p.getDescD());
                    errorCount++;
                    continue;
                }
                creditSubAccount = creditSubTpl.replace("{client}", mapped);
            }

            String date = p.getYear() + "/" + p.getMonth() + "/" + p.getDay();
            String tpl = rule.getSummaryTemplate() == null ? "{d}" : rule.getSummaryTemplate();
            String summary = tpl.replace("{d}", p.getDescD() == null ? "" : p.getDescD());

            outRows.add(CashBookPreviewRow.builder()
                    .excelRowIndex(p.getExcelRowIndex())
                    .transactionNo(txnNo++)
                    .transactionDate(date)
                    .debitAccount(rule.getDebitAccount())
                    .debitSubAccount(emptyIfNull(rule.getDebitSubAccount()))
                    .debitDepartment(emptyIfNull(rule.getDebitDepartment()))
                    .debitClient("")
                    .debitTax(MfTaxResolver.resolve(rule.getDebitTaxResolver(), p.getDescD()))
                    .debitInvoice("")
                    .debitAmount(amount)
                    .creditAccount(rule.getCreditAccount())
                    .creditSubAccount(emptyIfNull(creditSubAccount))
                    .creditDepartment(emptyIfNull(rule.getCreditDepartment()))
                    .creditClient("")
                    .creditTax(MfTaxResolver.resolve(rule.getCreditTaxResolver(), p.getDescD()))
                    .creditInvoice("")
                    .creditAmount(amount)
                    .summary(summary)
                    .tag("")
                    .memo("")
                    .descriptionC(p.getDescC())
                    .descriptionD(p.getDescD())
                    .build());
        }

        return CashBookPreviewResponse.builder()
                .uploadId(uploadId)
                .fileName(cached.getFileName())
                .totalRows(outRows.size())
                .errorCount(errorCount)
                .rows(outRows)
                .unmappedClients(new ArrayList<>(unmapped))
                .unknownDescriptions(new ArrayList<>(unknownC))
                .build();
    }

    private MMfJournalRule findRule(List<MMfJournalRule> rules, ParsedRow p) {
        return rules.stream()
                .filter(r -> normalize(r.getDescriptionC()).equals(p.getDescCNorm()))
                .filter(r -> {
                    if ("INCOME".equals(r.getAmountSource()) && p.getIncome() <= 0 && p.getPayment() > 0) return false;
                    if ("PAYMENT".equals(r.getAmountSource()) && p.getPayment() <= 0 && p.getIncome() > 0) return false;
                    return true;
                })
                .filter(r -> {
                    String kw = r.getDescriptionDKeyword();
                    if (kw == null || kw.isEmpty()) return true;
                    return p.getDescD() != null && p.getDescD().contains(kw);
                })
                .min(Comparator.comparingInt(r -> {
                    String kw = r.getDescriptionDKeyword();
                    int kwBonus = (kw == null || kw.isEmpty()) ? 1 : 0;
                    return r.getPriority() * 2 + kwBonus;
                }))
                .orElse(null);
    }

    private String resolveClient(List<MMfClientMapping> mappings, String descD) {
        if (descD == null) return null;
        for (MMfClientMapping m : mappings) {
            if (descD.contains(m.getAlias())) return m.getMfClientName();
        }
        return null;
    }

    private CashBookPreviewRow errorRow(ParsedRow p, String type, String msg) {
        return CashBookPreviewRow.builder()
                .excelRowIndex(p.getExcelRowIndex())
                .descriptionC(p.getDescC())
                .descriptionD(p.getDescD())
                .errorType(type)
                .errorMessage(msg)
                .build();
    }

    byte[] toCsvBytes(List<CashBookPreviewRow> rows) {
        StringBuilder sb = new StringBuilder();
        String[] headers = {
                "取引No", "取引日",
                "借方勘定科目", "借方補助科目", "借方部門", "借方取引先", "借方税区分", "借方インボイス", "借方金額(円)",
                "貸方勘定科目", "貸方補助科目", "貸方部門", "貸方取引先", "貸方税区分", "貸方インボイス", "貸方金額(円)",
                "摘要", "タグ", "メモ"
        };
        sb.append(String.join(",", headers)).append("\r\n");
        for (CashBookPreviewRow r : rows) {
            if (r.getErrorType() != null) continue;
            String[] cells = {
                    String.valueOf(r.getTransactionNo()),
                    nz(r.getTransactionDate()),
                    csvEscape(r.getDebitAccount()),
                    csvEscape(r.getDebitSubAccount()),
                    csvEscape(r.getDebitDepartment()),
                    csvEscape(r.getDebitClient()),
                    csvEscape(r.getDebitTax()),
                    csvEscape(r.getDebitInvoice()),
                    String.valueOf(r.getDebitAmount()),
                    csvEscape(r.getCreditAccount()),
                    csvEscape(r.getCreditSubAccount()),
                    csvEscape(r.getCreditDepartment()),
                    csvEscape(r.getCreditClient()),
                    csvEscape(r.getCreditTax()),
                    csvEscape(r.getCreditInvoice()),
                    String.valueOf(r.getCreditAmount()),
                    csvEscape(r.getSummary()),
                    csvEscape(r.getTag()),
                    csvEscape(r.getMemo())
            };
            sb.append(String.join(",", cells)).append("\r\n");
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 3);
        out.write(0xEF); out.write(0xBB); out.write(0xBF);
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static String csvEscape(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String emptyIfNull(String s) { return s == null ? "" : s; }

    private CachedUpload getCached(String uploadId) {
        CachedUpload cached = cache.get(uploadId);
        if (cached == null) throw new IllegalArgumentException("uploadIdが見つからないか有効期限切れです: " + uploadId);
        if (cached.getExpiresAt() < System.currentTimeMillis()) {
            cache.remove(uploadId);
            throw new IllegalArgumentException("uploadIdの有効期限が切れています: " + uploadId);
        }
        return cached;
    }

    // ---- セル読み取りユーティリティ ----

    private static Integer readIntegerCell(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) yield (int) v;
                yield null;
            }
            case STRING -> {
                String s = cell.getStringCellValue();
                if (s == null) yield null;
                try { yield Integer.parseInt(s.strip()); } catch (NumberFormatException e) { yield null; }
            }
            case FORMULA -> {
                try {
                    double v = cell.getNumericCellValue();
                    if (v == Math.floor(v) && !Double.isInfinite(v)) yield (int) v;
                    yield null;
                } catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    private static String readStringCell(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) yield String.valueOf((long) v);
                yield String.valueOf(v);
            }
            case BLANK -> "";
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (IllegalStateException e) {
                    try {
                        double v = cell.getNumericCellValue();
                        if (v == Math.floor(v)) yield String.valueOf((long) v);
                        yield String.valueOf(v);
                    } catch (Exception ex) { yield ""; }
                }
            }
            default -> "";
        };
    }

    private static int readIntCellOrZero(Cell cell) {
        if (cell == null) return 0;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> {
                    String s = cell.getStringCellValue();
                    if (s == null || s.isBlank()) yield 0;
                    try { yield (int) Double.parseDouble(s.strip()); } catch (NumberFormatException e) { yield 0; }
                }
                case FORMULA -> (int) cell.getNumericCellValue();
                default -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- 内部モデル ----

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static class ParsedRow {
        private int excelRowIndex;
        private int year;
        private int month;
        private int day;
        private String descC;
        private String descCNorm;
        private String descD;
        private int income;
        private int payment;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static class CachedUpload {
        private List<ParsedRow> rows;
        private String fileName;
        private String periodLabel;
        private long expiresAt;
    }
}
