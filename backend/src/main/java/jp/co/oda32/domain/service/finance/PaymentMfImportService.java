package jp.co.oda32.domain.service.finance;

import jakarta.annotation.PostConstruct;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 振込明細Excel → MoneyForward買掛仕入CSV 変換サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentMfImportService {

    private final MPaymentMfRuleRepository ruleRepository;
    private final TPaymentMfImportHistoryRepository historyRepository;
    private final TAccountsPayableSummaryRepository payableRepository;
    private final TAccountsPayableSummaryService payableService;

    // 差額一致閾値（買掛金一覧と共通）
    private static final long MATCH_THRESHOLD = 100L;
    private static final int DEFAULT_SHOP_NO = 1;
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 10000;
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final Charset CSV_CHARSET = Charset.forName("MS932");
    private static final String CSV_LINE_END = "\n";

    private static final List<String> CSV_HEADERS = List.of(
            "取引No", "取引日", "借方勘定科目", "借方補助科目", "借方部門", "借方取引先",
            "借方税区分", "借方インボイス", "借方金額(円)",
            "貸方勘定科目", "貸方補助科目", "貸方部門", "貸方取引先",
            "貸方税区分", "貸方インボイス", "貸方金額(円)",
            "摘要", "タグ", "メモ"
    );

    // 合計/メタ行のB列判定（正規化後の完全一致）
    private static final Set<String> META_EXACT = Set.of(
            "合計", "小計", "その他計", "本社仕入 合計", "請求額", "打ち込み額", "打込額",
            "本社仕入合計"
    );
    // 前方一致
    private static final List<String> META_PREFIX = List.of(
            "20日払い振込手数料", "5日払い振込手数料", "送金日"
    );

    private final Map<String, CachedUpload> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void initPoiSecurity() {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
    }

    // ===========================================================
    // Public API
    // ===========================================================

    public PaymentMfPreviewResponse preview(MultipartFile file) throws IOException {
        validateFile(file);
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = selectSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("振込明細シート（支払い明細/振込明細）が見つかりません");
            }
            ParsedWorkbook parsed = parseSheet(sheet);
            if (parsed.entries.size() > MAX_DATA_ROWS) {
                throw new IllegalArgumentException("データ行数上限を超過しています: " + parsed.entries.size());
            }

            enforceCacheLimit();
            String uploadId = UUID.randomUUID().toString();
            CachedUpload cached = CachedUpload.builder()
                    .entries(parsed.entries)
                    .transferDate(parsed.transferDate)
                    .summarySourceFee(parsed.summarySourceFee)
                    .summaryEarlyPayment(parsed.summaryEarlyPayment)
                    .fileName(sanitize(file.getOriginalFilename()))
                    .expiresAt(System.currentTimeMillis() + CACHE_TTL_MILLIS)
                    .build();
            cache.put(uploadId, cached);
            return buildPreview(uploadId, cached);
        }
    }

    public PaymentMfPreviewResponse rePreview(String uploadId) {
        CachedUpload cached = getCached(uploadId);
        return buildPreview(uploadId, cached);
    }

    /**
     * CSVバイト列を返す（CP932・LF・金額に末尾半角スペース）。未登録行があれば例外。
     */
    public byte[] convert(String uploadId, Integer userNo) {
        CachedUpload cached = getCached(uploadId);
        PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
        if (preview.getErrorCount() > 0) {
            throw new IllegalStateException(
                    "未登録の送り先があります（" + preview.getErrorCount() + "件）。マスタ登録後に再試行してください");
        }
        byte[] csv = toCsvBytes(preview.getRows(), cached.getTransferDate());
        saveHistory(cached, preview, csv, userNo);
        return csv;
    }

    /**
     * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
     * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
     */
    @org.springframework.transaction.annotation.Transactional
    public VerifyResult applyVerification(String uploadId, Integer userNo) {
        CachedUpload cached = getCached(uploadId);
        if (cached.getTransferDate() == null) {
            throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
        }
        LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
        String note = "振込明細検証 " + cached.getFileName() + " " + cached.getTransferDate();

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        int matched = 0, diff = 0, notFound = 0, skipped = 0;
        List<String> unmatchedSuppliers = new ArrayList<>();

        for (ParsedEntry e : cached.getEntries()) {
            // PAYABLE行のみ対象（合計後の20日払いセクションは対象外）
            if (e.afterTotal) { skipped++; continue; }
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
            if (e.supplierCode == null) { skipped++; continue; }

            List<TAccountsPayableSummary> list = payableRepository
                    .findByShopNoAndSupplierCodeAndTransactionMonth(DEFAULT_SHOP_NO, e.supplierCode, txMonth);
            if (list.isEmpty()) {
                notFound++;
                unmatchedSuppliers.add(e.sourceName + "(" + e.supplierCode + ")");
                continue;
            }

            BigDecimal payable = BigDecimal.ZERO;
            for (TAccountsPayableSummary s : list) {
                BigDecimal v = s.getTaxIncludedAmountChange() != null
                        ? s.getTaxIncludedAmountChange() : s.getTaxIncludedAmount();
                if (v != null) payable = payable.add(v);
            }
            BigDecimal invoice = BigDecimal.valueOf(e.amount);
            BigDecimal difference = payable.subtract(invoice);
            boolean isMatched = difference.abs().compareTo(BigDecimal.valueOf(MATCH_THRESHOLD)) <= 0;
            if (isMatched) matched++; else diff++;

            for (TAccountsPayableSummary s : list) {
                s.setVerificationResult(isMatched ? 1 : 0);
                s.setPaymentDifference(difference);
                s.setVerifiedManually(true);
                s.setMfExportEnabled(isMatched);
                s.setVerificationNote(note);
                payableService.save(s);
            }
        }

        return VerifyResult.builder()
                .transferDate(cached.getTransferDate())
                .transactionMonth(txMonth)
                .matchedCount(matched)
                .diffCount(diff)
                .notFoundCount(notFound)
                .skippedCount(skipped)
                .unmatchedSuppliers(unmatchedSuppliers)
                .build();
    }

    @Data
    @Builder
    public static class VerifyResult {
        private LocalDate transferDate;
        private LocalDate transactionMonth;
        private int matchedCount;
        private int diffCount;
        private int notFoundCount;
        private int skippedCount;
        private List<String> unmatchedSuppliers;
    }

    public byte[] getHistoryCsv(Integer id) {
        return historyRepository.findById(id)
                .map(TPaymentMfImportHistory::getCsvBody)
                .orElseThrow(() -> new IllegalArgumentException("履歴が見つかりません: " + id));
    }

    // ===========================================================
    // Preview building (rule matching + 買掛金 reconciliation)
    // ===========================================================

    private PaymentMfPreviewResponse buildPreview(String uploadId, CachedUpload cached) {
        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        LocalDate txMonth = cached.getTransferDate() == null ? null
                : deriveTransactionMonth(cached.getTransferDate());

        List<PaymentMfPreviewRow> rows = new ArrayList<>();
        Set<String> unregistered = new java.util.LinkedHashSet<>();
        int matched = 0, diff = 0, unmatched = 0, errors = 0;
        long totalAmount = 0L;

        for (ParsedEntry e : cached.getEntries()) {
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));

            // 合計行以降(20日払いセクション) は買掛金(PAYABLE)ではなく仕入高(DIRECT_PURCHASE)扱い
            if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
                rule = MPaymentMfRule.builder()
                        .sourceName(rule.getSourceName())
                        .ruleKind("DIRECT_PURCHASE")
                        .debitAccount("仕入高")
                        .debitSubAccount(null)
                        .debitDepartment(null)
                        .debitTaxCategory("課税仕入 10%")
                        .creditAccount("資金複合")
                        .creditTaxCategory("対象外")
                        .summaryTemplate("{source_name}")
                        .build();
            }

            PaymentMfPreviewRow.PaymentMfPreviewRowBuilder b = PaymentMfPreviewRow.builder()
                    .excelRowIndex(e.rowIndex)
                    .paymentSupplierCode(e.supplierCode)
                    .sourceName(e.sourceName)
                    .amount(e.amount);

            if (rule == null) {
                unregistered.add(e.sourceName);
                errors++;
                rows.add(b.errorType("UNREGISTERED")
                        .errorMessage("マスタに未登録: " + e.sourceName)
                        .matchStatus("UNMATCHED")
                        .build());
                unmatched++;
                continue;
            }

            totalAmount += e.amount;
            b.ruleKind(rule.getRuleKind())
                    .debitAccount(rule.getDebitAccount())
                    .debitSubAccount(rule.getDebitSubAccount())
                    .debitDepartment(rule.getDebitDepartment())
                    .debitTax(rule.getDebitTaxCategory())
                    .debitAmount(e.amount)
                    .creditAccount(rule.getCreditAccount())
                    .creditSubAccount(rule.getCreditSubAccount())
                    .creditDepartment(rule.getCreditDepartment())
                    .creditTax(rule.getCreditTaxCategory())
                    .creditAmount(e.amount)
                    .summary(renderSummary(rule, e))
                    .tag(rule.getTag());

            if ("PAYABLE".equals(rule.getRuleKind()) && txMonth != null && e.supplierCode != null) {
                ReconcileResult rr = reconcile(e.supplierCode, txMonth, e.amount);
                b.matchStatus(rr.status).payableAmount(rr.payableAmount)
                        .payableDiff(rr.diff).supplierNo(rr.supplierNo);
                if ("MATCHED".equals(rr.status)) matched++;
                else if ("DIFF".equals(rr.status)) diff++;
                else unmatched++;
            } else {
                b.matchStatus("NA");
            }
            rows.add(b.build());
        }

        // 合計行からサマリー仕訳（振込手数料値引・早払収益）を末尾2行として追加
        if (cached.getTransferDate() != null) {
            int d = cached.getTransferDate().getDayOfMonth();
            long fee = cached.getSummarySourceFee() == null ? 0L : cached.getSummarySourceFee();
            long early = cached.getSummaryEarlyPayment() == null ? 0L : cached.getSummaryEarlyPayment();
            rows.add(PaymentMfPreviewRow.builder()
                    .ruleKind("SUMMARY")
                    .sourceName("振込手数料値引")
                    .amount(fee)
                    .debitAccount("資金複合").debitTax("対象外").debitAmount(fee)
                    .creditAccount("仕入値引・戻し高").creditDepartment("物販事業部")
                    .creditTax("課税仕入-返還等 10%").creditAmount(fee)
                    .summary("振込手数料値引／" + d + "日払い分")
                    .matchStatus("NA").build());
            rows.add(PaymentMfPreviewRow.builder()
                    .ruleKind("SUMMARY")
                    .sourceName("早払収益")
                    .amount(early)
                    .debitAccount("資金複合").debitTax("対象外").debitAmount(early)
                    .creditAccount("早払収益").creditDepartment("物販事業部")
                    .creditTax("非課税売上").creditAmount(early)
                    .summary("早払収益／" + d + "日払い分")
                    .matchStatus("NA").build());
        }

        return PaymentMfPreviewResponse.builder()
                .uploadId(uploadId)
                .fileName(cached.getFileName())
                .transferDate(cached.getTransferDate())
                .transactionMonth(txMonth)
                .totalRows(rows.size())
                .totalAmount(totalAmount)
                .matchedCount(matched)
                .diffCount(diff)
                .unmatchedCount(unmatched)
                .errorCount(errors)
                .rows(rows)
                .unregisteredSources(new ArrayList<>(unregistered))
                .build();
    }

    private String renderSummary(MPaymentMfRule rule, ParsedEntry e) {
        String tpl = rule.getSummaryTemplate();
        if (tpl == null || tpl.isEmpty()) return e.sourceName;
        String sub = rule.getDebitSubAccount() != null ? rule.getDebitSubAccount() : e.sourceName;
        return tpl.replace("{sub_account}", sub)
                  .replace("{source_name}", e.sourceName);
    }

    LocalDate deriveTransactionMonth(LocalDate transferDate) {
        int day = transferDate.getDayOfMonth();
        if (day == 5) return transferDate.minusMonths(1).withDayOfMonth(20);
        return transferDate.withDayOfMonth(20);
    }

    private static class ReconcileResult {
        String status; Long payableAmount; Long diff; Integer supplierNo;
    }

    private ReconcileResult reconcile(String supplierCode, LocalDate txMonth, Long invoiceAmount) {
        ReconcileResult r = new ReconcileResult();
        List<TAccountsPayableSummary> list = payableRepository
                .findByShopNoAndSupplierCodeAndTransactionMonth(DEFAULT_SHOP_NO, supplierCode, txMonth);
        if (list.isEmpty()) {
            r.status = "UNMATCHED";
            return r;
        }
        long payable = 0L;
        Integer supplierNo = null;
        for (TAccountsPayableSummary s : list) {
            BigDecimal v = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange()
                    : s.getTaxIncludedAmount();
            if (v != null) payable += v.longValueExact();
            if (supplierNo == null) supplierNo = s.getSupplierNo();
        }
        r.payableAmount = payable;
        r.diff = payable - invoiceAmount;
        r.supplierNo = supplierNo;
        r.status = Math.abs(r.diff) <= MATCH_THRESHOLD ? "MATCHED" : "DIFF";
        return r;
    }

    // ===========================================================
    // Excel sheet selection & parsing
    // ===========================================================

    Sheet selectSheet(Workbook workbook) {
        Sheet byExactPayment = null;
        Sheet byExactTransfer = null;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String n = workbook.getSheetName(i);
            if (n == null) continue;
            if (n.contains("MF") || n.contains("変換MAP") || n.contains("福通")) continue;
            if ("支払い明細".equals(n)) byExactPayment = workbook.getSheetAt(i);
            else if ("振込明細".equals(n)) byExactTransfer = workbook.getSheetAt(i);
        }
        if (byExactPayment != null) return byExactPayment;
        if (byExactTransfer != null) return byExactTransfer;
        // フォールバック
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String n = workbook.getSheetName(i);
            if (n != null && (n.contains("支払") || n.contains("振込"))
                    && !n.contains("MF") && !n.contains("変換") && !n.contains("福通")) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    ParsedWorkbook parseSheet(Sheet sheet) {
        ParsedWorkbook out = new ParsedWorkbook();
        out.entries = new ArrayList<>();

        // 送金日: 1行目の日付セルをスキャン（通常はE1）
        Row r1 = sheet.getRow(0);
        if (r1 != null) {
            for (int c = 0; c <= 10; c++) {
                LocalDate d = readDateCell(r1.getCell(c));
                if (d != null) { out.transferDate = d; break; }
            }
        }

        // ヘッダ行（2行目）から列マップ構築
        Row header = sheet.getRow(1);
        if (header == null) throw new IllegalArgumentException("ヘッダ行（2行目）が見つかりません");
        Map<String, Integer> colMap = buildColumnMap(header);
        Integer colCode = colMap.getOrDefault("仕入コード", -1); // 20日払いはヘッダ空の場合A列数値で流用
        Integer colSource = colMap.get("送り先");
        Integer colAmount = colMap.get("請求額");
        Integer colFee = colMap.get("送料相手");
        Integer colEarly = colMap.get("早払い");
        if (colSource == null || colAmount == null) {
            throw new IllegalArgumentException("ヘッダ『送り先』『請求額』が特定できません");
        }

        int last = sheet.getLastRowNum();
        boolean summaryCaptured = false;
        boolean afterTotal = false;
        for (int i = 2; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String sourceName = readStringCell(row.getCell(colSource));
            String sourceNorm = normalize(sourceName);
            Long amount = readLongCell(row.getCell(colAmount));

            // 合計行の処理（サマリー抽出 + セクション切替）
            if (sourceNorm != null && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
                if (!summaryCaptured) {
                    out.summarySourceFee = colFee == null ? null : readLongCell(row.getCell(colFee));
                    out.summaryEarlyPayment = colEarly == null ? null : readLongCell(row.getCell(colEarly));
                    summaryCaptured = true;
                    afterTotal = true;
                }
                continue;
            }
            // メタ行スキップ
            if (isMetaRow(sourceNorm)) continue;
            // 金額ゼロ/未入力はスキップ
            if (amount == null || amount == 0L) continue;
            if (sourceName == null || sourceName.isBlank()) continue;

            String supplierCode = null;
            if (colCode != null && colCode >= 0) {
                supplierCode = readStringCell(row.getCell(colCode));
                if (supplierCode != null) {
                    supplierCode = supplierCode.trim();
                    if (supplierCode.isEmpty() || !supplierCode.chars().allMatch(Character::isDigit)) {
                        supplierCode = null;
                    }
                }
            }

            ParsedEntry pe = new ParsedEntry();
            pe.rowIndex = i + 1;
            pe.supplierCode = supplierCode;
            pe.sourceName = sourceName.trim();
            pe.amount = amount;
            pe.afterTotal = afterTotal;
            out.entries.add(pe);
        }
        return out;
    }

    private Map<String, Integer> buildColumnMap(Row header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String v = readStringCell(header.getCell(c));
            if (v == null) continue;
            String n = normalize(v);
            if (n.isEmpty()) continue;
            map.putIfAbsent(n, c);
        }
        return map;
    }

    private boolean isTotalRow(Row row, int colSource) {
        // A列やB列が「合計」という明示
        for (int c = 0; c < Math.min(row.getLastCellNum(), colSource + 2); c++) {
            String v = normalize(readStringCell(row.getCell(c)));
            if ("合計".equals(v)) return true;
        }
        return false;
    }

    private boolean isMetaRow(String normalized) {
        if (normalized == null || normalized.isEmpty()) return true;
        if (META_EXACT.contains(normalized)) return true;
        for (String p : META_PREFIX) if (normalized.startsWith(p)) return true;
        return false;
    }

    // ===========================================================
    // CSV generation
    // ===========================================================

    private byte[] toCsvBytes(List<PaymentMfPreviewRow> rows, LocalDate transferDate) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CSV_CHARSET)) {
            w.write(String.join(",", CSV_HEADERS));
            w.write(CSV_LINE_END);
            String date = transferDate == null ? "" : transferDate.format(CSV_DATE);
            for (PaymentMfPreviewRow r : rows) {
                if ("UNREGISTERED".equals(r.getErrorType())) continue; // エラー行は出力しない
                w.write(toCsvLine(r, date));
                w.write(CSV_LINE_END);
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV出力に失敗しました", e);
        }
        return baos.toByteArray();
    }

    private String toCsvLine(PaymentMfPreviewRow r, String date) {
        List<String> cols = new ArrayList<>(19);
        cols.add("");                                // 取引No
        cols.add(date);                              // 取引日
        cols.add(safe(r.getDebitAccount()));
        cols.add(safe(r.getDebitSubAccount()));
        cols.add(safe(r.getDebitDepartment()));
        cols.add("");                                // 借方取引先
        cols.add(safe(r.getDebitTax()));
        cols.add("");                                // 借方インボイス
        cols.add(fmtAmount(r.getDebitAmount()));
        cols.add(safe(r.getCreditAccount()));
        cols.add(safe(r.getCreditSubAccount()));
        cols.add(safe(r.getCreditDepartment()));
        cols.add("");                                // 貸方取引先
        cols.add(safe(r.getCreditTax()));
        cols.add("");                                // 貸方インボイス
        cols.add(fmtAmount(r.getCreditAmount()));
        cols.add(safe(r.getSummary()));
        cols.add(safe(r.getTag()));
        cols.add("");                                // メモ
        return String.join(",", cols);
    }

    private static String fmtAmount(Long v) {
        if (v == null) return "";
        return v + " "; // 金額後ろに半角スペース（既存運用CSVに合わせる）
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ===========================================================
    // History
    // ===========================================================

    private void saveHistory(CachedUpload cached, PaymentMfPreviewResponse preview,
                             byte[] csv, Integer userNo) {
        try {
            String yyyymmdd = cached.getTransferDate() == null ? "unknown"
                    : cached.getTransferDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String csvFile = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
            TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                    .shopNo(DEFAULT_SHOP_NO)
                    .transferDate(cached.getTransferDate())
                    .sourceFilename(cached.getFileName())
                    .csvFilename(csvFile)
                    .rowCount(preview.getTotalRows())
                    .totalAmount(preview.getTotalAmount())
                    .matchedCount(preview.getMatchedCount())
                    .diffCount(preview.getDiffCount())
                    .unmatchedCount(preview.getUnmatchedCount())
                    .csvBody(csv)
                    .addDateTime(LocalDateTime.now())
                    .addUserNo(userNo)
                    .build();
            historyRepository.save(h);
        } catch (Exception e) {
            log.error("変換履歴の保存に失敗（CSV出力は正常完了）", e);
        }
    }

    // ===========================================================
    // Helpers
    // ===========================================================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("ファイルが空です");
        if (file.getSize() > MAX_UPLOAD_BYTES)
            throw new IllegalArgumentException("ファイルサイズ上限(20MB)を超過しています");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx"))
            throw new IllegalArgumentException("xlsxファイルのみ対応しています");
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank() && !XLSX_CONTENT_TYPE.equals(ct)
                && !"application/octet-stream".equals(ct)) {
            throw new IllegalArgumentException("xlsxファイルのみ対応しています");
        }
    }

    private CachedUpload getCached(String uploadId) {
        CachedUpload c = cache.get(uploadId);
        if (c == null || c.getExpiresAt() < System.currentTimeMillis())
            throw new IllegalArgumentException("アップロードが見つからないか期限切れです。再アップロードしてください");
        return c;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    void cleanExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
    }

    private synchronized void enforceCacheLimit() {
        if (cache.size() < MAX_CACHE_ENTRIES) return;
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
        int guard = MAX_CACHE_ENTRIES + 10;
        while (cache.size() >= MAX_CACHE_ENTRIES && guard-- > 0) {
            String oldest = cache.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().getExpiresAt()))
                    .map(Map.Entry::getKey).orElse(null);
            if (oldest == null) break;
            cache.remove(oldest);
        }
    }

    private static String sanitize(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\n]", "_");
    }

    /** 全角空白・前後空白除去、正規化比較用 */
    static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u3000', ' ').strip().replaceAll("\\s+", " ");
    }

    static String readStringCell(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d))
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double d2 = cell.getNumericCellValue();
                        if (d2 == Math.floor(d2)) yield String.valueOf((long) d2);
                        yield String.valueOf(d2);
                    } catch (Exception e2) { yield null; }
                }
            }
            default -> null;
        };
    }

    static Long readLongCell(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (long) cell.getNumericCellValue();
                case STRING -> {
                    String s = cell.getStringCellValue().trim();
                    if (s.isEmpty()) yield null;
                    try { yield Long.parseLong(s.replace(",", "")); }
                    catch (NumberFormatException e) { yield null; }
                }
                case FORMULA -> {
                    try { yield (long) cell.getNumericCellValue(); }
                    catch (Exception e) { yield null; }
                }
                default -> null;
            };
        } catch (Exception e) { return null; }
    }

    static LocalDate readDateCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double d = cell.getNumericCellValue();
                // Excelの日付シリアル値範囲で date formatted が効かないケースのフォールバック
                if (d > 40000 && d < 100000) {
                    return DateUtil.getLocalDateTime(d).toLocalDate();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ===========================================================
    // DTOs (internal)
    // ===========================================================

    @Data
    @Builder
    static class CachedUpload {
        private List<ParsedEntry> entries;
        private LocalDate transferDate;
        private Long summarySourceFee;
        private Long summaryEarlyPayment;
        private String fileName;
        private long expiresAt;
    }

    static class ParsedEntry {
        int rowIndex;
        String supplierCode;
        String sourceName;
        Long amount;
        boolean afterTotal;
    }

    static class ParsedWorkbook {
        List<ParsedEntry> entries;
        LocalDate transferDate;
        Long summarySourceFee;
        Long summaryEarlyPayment;
    }
}
