package jp.co.oda32.domain.service.finance;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 振込明細Excel → MoneyForward買掛仕入CSV 変換サービス
 */
@Service
@Slf4j
public class PaymentMfImportService {

    private final MPaymentMfRuleRepository ruleRepository;
    private final TPaymentMfImportHistoryRepository historyRepository;
    private final TAccountsPayableSummaryRepository payableRepository;
    private final TAccountsPayableSummaryService payableService;
    private final TPaymentMfAuxRowRepository auxRowRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
     * Spring AOP プロキシ経由になるようにするため。{@code @Lazy} は循環依存回避。
     */
    @Autowired
    @Lazy
    private PaymentMfImportService self;

    // 差額一致閾値は FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE(_LONG) に集約。
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 10000;
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 取引月単位の applyVerification / exportVerifiedCsv を直列化する advisory lock 用キー。
     * shop_no と transaction_month(epochDay) を混ぜて 64bit のキーに詰める。
     */
    private static final int ADVISORY_LOCK_CLASS = 0x7041_4D46; // 'pAMF'

    // CSV 生成ロジックは {@link PaymentMfCsvWriter} に分離（ステートレスユーティリティ）。

    // Excel 読み取り（selectSheet / parseSheet / メタ行判定 など）は
    // {@link PaymentMfExcelParser} に分離。ParsedEntry / ParsedWorkbook も同クラスに移動。

    private final Map<String, CachedUpload> cache = new ConcurrentHashMap<>();

    public PaymentMfImportService(MPaymentMfRuleRepository ruleRepository,
                                  TPaymentMfImportHistoryRepository historyRepository,
                                  TAccountsPayableSummaryRepository payableRepository,
                                  TAccountsPayableSummaryService payableService,
                                  TPaymentMfAuxRowRepository auxRowRepository) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
        this.payableRepository = payableRepository;
        this.payableService = payableService;
        this.auxRowRepository = auxRowRepository;
    }

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
            Sheet sheet = PaymentMfExcelParser.selectSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("振込明細シート（支払い明細/振込明細）が見つかりません");
            }
            PaymentMfExcelParser.ParsedWorkbook parsed = PaymentMfExcelParser.parseSheet(sheet);
            if (parsed.entries.size() > MAX_DATA_ROWS) {
                throw new IllegalArgumentException("データ行数上限を超過しています: " + parsed.entries.size());
            }

            String uploadId = UUID.randomUUID().toString();
            CachedUpload cached = CachedUpload.builder()
                    .entries(parsed.entries)
                    .transferDate(parsed.transferDate)
                    .summarySourceFee(parsed.summarySourceFee)
                    .summaryEarlyPayment(parsed.summaryEarlyPayment)
                    .summaryTransferAmount(parsed.summaryTransferAmount)
                    .summaryInvoiceTotal(parsed.summaryInvoiceTotal)
                    .fileName(PaymentMfCellReader.sanitize(file.getOriginalFilename()))
                    .expiresAt(System.currentTimeMillis() + CACHE_TTL_MILLIS)
                    .build();
            putCacheAtomically(uploadId, cached);
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
        // CSV 取引日列は 小田光の締め日(前月20日 = transactionMonth) 固定。
        // 送金日(送金日≠取引日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
        LocalDate txMonth = cached.getTransferDate() == null ? null
                : deriveTransactionMonth(cached.getTransferDate());
        byte[] csv = PaymentMfCsvWriter.toCsvBytes(preview.getRows(), txMonth);
        saveHistory(cached, preview, csv, userNo);
        return csv;
    }

    /**
     * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
     * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
     *
     * <p>並列呼び出しでの上書き競合を防ぐため、同一 (shop_no, transaction_month) に対しては
     * PostgreSQL advisory lock で直列化する。5日払い Excel と 20日払い Excel が別プロセス/
     * 別 UI から同時適用された場合でも、後着は先行 tx のコミット完了後に実行される。
     */
    @Transactional
    public VerifyResult applyVerification(String uploadId, Integer userNo) {
        CachedUpload cached = getCached(uploadId);
        if (cached.getTransferDate() == null) {
            throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
        }
        LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
        acquireAdvisoryLock(txMonth);

        String note = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX
                + cached.getFileName() + " " + cached.getTransferDate();

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        // 事前パス: 当該 Excel に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
        Set<String> codesToReconcile = new java.util.LinkedHashSet<>();
        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            if (e.afterTotal) continue;
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) continue;
            String reconcileCode = e.supplierCode != null ? e.supplierCode
                    : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                            ? rule.getPaymentSupplierCode() : null);
            if (reconcileCode != null) codesToReconcile.add(reconcileCode);
        }
        Map<String, List<TAccountsPayableSummary>> payablesByCode = codesToReconcile.isEmpty()
                ? Map.of()
                : payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, codesToReconcile, txMonth)
                    .stream()
                    .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));

        int matched = 0, diff = 0, notFound = 0, skipped = 0;
        List<String> unmatchedSuppliers = new ArrayList<>();
        BigDecimal matchThreshold = FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE;

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            // PAYABLE行のみ対象（合計後の20日払いセクションは対象外）
            if (e.afterTotal) { skipped++; continue; }
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
            String reconcileCode = e.supplierCode != null ? e.supplierCode
                    : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                            ? rule.getPaymentSupplierCode() : null);
            if (reconcileCode == null) { skipped++; continue; }

            List<TAccountsPayableSummary> list = payablesByCode.getOrDefault(reconcileCode, List.of());
            if (list.isEmpty()) {
                notFound++;
                unmatchedSuppliers.add(e.sourceName + "(" + reconcileCode + ")");
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
            boolean isMatched = difference.abs().compareTo(matchThreshold) <= 0;
            if (isMatched) matched++; else diff++;

            // 振込明細の請求額は支払先単位で1件だが、DBは税率別に複数行ある場合がある。
            // UI 表示用途のため、全税率行に同じ verified_amount を書き込む
            // （税率別の請求額内訳は Excel 側に存在しないため、合計値を代表値として保持）。
            for (TAccountsPayableSummary s : list) {
                s.setVerificationResult(isMatched ? 1 : 0);
                s.setPaymentDifference(difference);
                s.setVerifiedManually(true);
                s.setMfExportEnabled(isMatched);
                s.setVerificationNote(note);
                s.setVerifiedAmount(invoice);
                payableService.save(s);
            }
        }

        // 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
        // preview を applyVerification 内で 1 回だけ構築し、aux row 生成にそのまま使い回す
        // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
        PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
        saveAuxRowsForVerification(cached, preview, txMonth, userNo);

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

    /**
     * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
     * 直列化する。PostgreSQL の {@code pg_advisory_xact_lock} は現在のトランザクション終了時に
     * 自動解放されるため、解放漏れリスクが無い。
     */
    private void acquireAdvisoryLock(LocalDate transactionMonth) {
        long lockKey = ((long) ADVISORY_LOCK_CLASS << 32)
                | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", lockKey)
                .getSingleResult();
    }

    /**
     * applyVerification 時に EXPENSE/SUMMARY/DIRECT_PURCHASE 行を
     * {@code t_payment_mf_aux_row} に洗い替え保存する。
     * PAYABLE 行は {@code t_accounts_payable_summary} 側で管理するためここでは対象外。
     * UNREGISTERED 行は CSV に出ないため保存しない。
     *
     * @param preview applyVerification で既に構築済みの preview を使い回す
     *                （再 buildPreview で N+1 を再度走らせないため）。
     */
    private void saveAuxRowsForVerification(CachedUpload cached, PaymentMfPreviewResponse preview,
                                            LocalDate txMonth, Integer userNo) {
        LocalDate transferDate = cached.getTransferDate();
        int deleted = auxRowRepository.deleteByShopAndTransactionMonthAndTransferDate(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate);
        if (deleted > 0) {
            log.info("補助行を洗い替え: shop={} txMonth={} transferDate={} 削除={}件",
                    FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, deleted);
        }

        LocalDateTime now = LocalDateTime.now();
        int seq = 0;
        List<TPaymentMfAuxRow> toSave = new ArrayList<>();
        for (PaymentMfPreviewRow row : preview.getRows()) {
            String ruleKind = row.getRuleKind();
            if (ruleKind == null) continue;
            if ("PAYABLE".equals(ruleKind)) continue;
            if ("UNREGISTERED".equals(row.getErrorType())) continue;
            if (row.getAmount() == null) continue;

            toSave.add(TPaymentMfAuxRow.builder()
                    .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                    .transactionMonth(txMonth)
                    .transferDate(transferDate)
                    .ruleKind(ruleKind)
                    .sequenceNo(seq++)
                    .sourceName(row.getSourceName())
                    .paymentSupplierCode(row.getPaymentSupplierCode())
                    .amount(BigDecimal.valueOf(row.getAmount()))
                    .debitAccount(row.getDebitAccount())
                    .debitSubAccount(row.getDebitSubAccount())
                    .debitDepartment(row.getDebitDepartment())
                    .debitTax(row.getDebitTax())
                    .creditAccount(row.getCreditAccount())
                    .creditSubAccount(row.getCreditSubAccount())
                    .creditDepartment(row.getCreditDepartment())
                    .creditTax(row.getCreditTax())
                    .summary(row.getSummary())
                    .tag(row.getTag())
                    .sourceFilename(cached.getFileName())
                    .addDateTime(now)
                    .addUserNo(userNo)
                    .build());
        }
        if (!toSave.isEmpty()) {
            auxRowRepository.saveAll(toSave);
        }
        log.info("補助行を保存: shop={} txMonth={} transferDate={} 追加={}件",
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, toSave.size());
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

    /**
     * BigDecimal を long に変換する（小数点以下は切り捨て）。
     * 手入力由来の verifiedAmount などで scale&gt;0 が混入しても例外にならないよう
     * 明示的に {@link RoundingMode#DOWN} で丸める。
     */
    private static long toLongFloor(BigDecimal v) {
        return v.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /**
     * 同一 supplier_no に属する税率別行群から、MF出力用の合計額を算出する。
     * <ul>
     *   <li>振込明細一括検証では全行に同値の verified_amount が書き込まれるため、
     *       SUM すると件数倍の重複になる。このケースは代表1行の値を採用する。</li>
     *   <li>手動 verify では税率別に異なる値が入り得るため、税率行の SUM を採用する。</li>
     * </ul>
     * 判定は verifiedAmount が全行一致なら代表値、そうでなければ SUM。
     * verifiedAmount が null の行は taxIncludedAmountChange にフォールバック。
     */
    private static long sumVerifiedAmountForGroup(List<TAccountsPayableSummary> group) {
        if (group.isEmpty()) return 0L;
        List<Long> perRow = new ArrayList<>(group.size());
        for (TAccountsPayableSummary s : group) {
            BigDecimal v = s.getVerifiedAmount() != null ? s.getVerifiedAmount()
                    : s.getTaxIncludedAmountChange();
            perRow.add(v == null ? 0L : toLongFloor(v));
        }
        long first = perRow.get(0);
        boolean allSame = perRow.stream().allMatch(x -> x == first);
        if (allSame) return first;
        return perRow.stream().mapToLong(Long::longValue).sum();
    }

    // ===========================================================
    // 検証済み買掛金からの MF CSV 出力 (Excel 再アップロード不要)
    // ===========================================================

    @Data
    @Builder
    public static class VerifiedExportResult {
        private byte[] csv;
        private int rowCount;
        private int payableCount;
        private int auxCount;
        private long totalAmount;
        /** ルール未登録で CSV 行を生成できず除外した supplier_code + supplier_name */
        private List<String> skippedSuppliers;
        private LocalDate transactionMonth;
    }

    /**
     * {@code t_accounts_payable_summary} の「検証結果=一致 かつ MF出力=ON」行から、
     * Excel 再アップロード無しで MF 仕訳 CSV を生成する。
     * <p>生成される CSV は <b>PAYABLE(買掛金)行のみ</b>。振込明細 Excel 由来の
     * 費用仕訳 (EXPENSE) / 直接仕入高 (DIRECT_PURCHASE) / 振込手数料値引・早払収益 (SUMMARY)
     * は DB に保持されていないため含まれない。それらが必要な場合は Excel 取込フローを使うこと。
     * <p>CSV「取引日」列は 小田光の締め日 = {@code transactionMonth}(前月20日) 固定。
     * 支払日(送金日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
     *
     * @param transactionMonth 対象取引月 (例: 2026-01-20)。CSV 取引日列にも使用。
     * @param userNo           履歴保存用ユーザ番号
     */
    @Transactional
    public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");
        acquireAdvisoryLock(transactionMonth);

        List<TAccountsPayableSummary> dbRows = payableRepository.findVerifiedForMfExport(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
        List<TPaymentMfAuxRow> auxRows = auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);

        if (dbRows.isEmpty() && auxRows.isEmpty()) {
            throw new IllegalStateException(
                    "対象取引月(" + transactionMonth + ")に出力対象データがありません"
                    + "（一致・MF出力ONの買掛金 0件、補助行 0件）");
        }

        // supplier_no 単位に集約する。verified_amount は振込明細一括検証時は税率別同値だが、
        // 手動 verify では税率別に異なる値が入り得るため、代表1行ではなく税率横断で SUM する。
        Map<Integer, List<TAccountsPayableSummary>> bySupplierNo = dbRows.stream()
                .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));

        // ルールを payment_supplier_code で引けるように
        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
        }

        List<PaymentMfPreviewRow> csvRows = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        long totalAmount = 0L;

        for (List<TAccountsPayableSummary> group : bySupplierNo.values()) {
            TAccountsPayableSummary s = group.get(0);
            MPaymentMfRule rule = s.getSupplierCode() == null ? null : byCode.get(s.getSupplierCode());
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) {
                skipped.add(s.getSupplierCode() + " (supplier_no=" + s.getSupplierNo()
                        + (rule == null ? " ルール未登録" : " 非PAYABLE=" + rule.getRuleKind()) + ")");
                continue;
            }

            long amount = sumVerifiedAmountForGroup(group);
            if (amount == 0L) continue;
            totalAmount += amount;

            String sourceName = rule.getSourceName();
            PaymentMfPreviewRow row = PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(s.getSupplierCode())
                    .sourceName(sourceName)
                    .amount(amount)
                    .ruleKind(rule.getRuleKind())
                    .debitAccount(rule.getDebitAccount())
                    .debitSubAccount(rule.getDebitSubAccount())
                    .debitDepartment(rule.getDebitDepartment())
                    .debitTax(rule.getDebitTaxCategory())
                    .debitAmount(amount)
                    .creditAccount(rule.getCreditAccount())
                    .creditSubAccount(rule.getCreditSubAccount())
                    .creditDepartment(rule.getCreditDepartment())
                    .creditTax(rule.getCreditTaxCategory())
                    .creditAmount(amount)
                    .summary(renderSummary(rule, sourceName))
                    .tag(rule.getTag())
                    .matchStatus("MATCHED")
                    .build();
            csvRows.add(row);
        }

        int payableCount = csvRows.size();

        // 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) を末尾に追加 (transferDate ASC, sequenceNo ASC)
        for (TPaymentMfAuxRow aux : auxRows) {
            long amount = aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount());
            totalAmount += amount;
            csvRows.add(PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(aux.getPaymentSupplierCode())
                    .sourceName(aux.getSourceName())
                    .amount(amount)
                    .ruleKind(aux.getRuleKind())
                    .debitAccount(aux.getDebitAccount())
                    .debitSubAccount(aux.getDebitSubAccount())
                    .debitDepartment(aux.getDebitDepartment())
                    .debitTax(aux.getDebitTax())
                    .debitAmount(amount)
                    .creditAccount(aux.getCreditAccount())
                    .creditSubAccount(aux.getCreditSubAccount())
                    .creditDepartment(aux.getCreditDepartment())
                    .creditTax(aux.getCreditTax())
                    .creditAmount(amount)
                    .summary(aux.getSummary())
                    .tag(aux.getTag())
                    .matchStatus("NA")
                    .build());
        }
        int auxCount = csvRows.size() - payableCount;

        if (csvRows.isEmpty()) {
            throw new IllegalStateException("CSV 出力可能な行がありません（ルール未登録のみ・補助行なし）: " + skipped);
        }

        // CSV 取引日列は 締め日 = transactionMonth を使用
        byte[] csv = PaymentMfCsvWriter.toCsvBytes(csvRows, transactionMonth);
        try {
            // REQUIRES_NEW を proxy 経由で発動させるため self. で自己呼び出しする
            // (this. だと Spring AOP を経由せず REQUIRES_NEW が無視され、履歴保存失敗時に
            //  本体 tx が巻き戻って手動検証結果が失われる)。
            self.saveVerifiedExportHistory(transactionMonth, csvRows.size(), totalAmount, csv, userNo);
        } catch (Exception e) {
            log.error("検証済み CSV 出力履歴の保存に失敗（CSV は正常完了）: transactionMonth={}", transactionMonth, e);
        }

        return VerifiedExportResult.builder()
                .csv(csv)
                .rowCount(csvRows.size())
                .payableCount(payableCount)
                .auxCount(auxCount)
                .totalAmount(totalAmount)
                .skippedSuppliers(skipped)
                .transactionMonth(transactionMonth)
                .build();
    }

    /**
     * 検証済みCSV出力ダイアログのプレビュー情報を返す（ダウンロード前の件数確認用）。
     * 5日/20日 片方の振込明細が未取込の場合は警告文字列を含める。
     */
    @Transactional(readOnly = true)
    public VerifiedExportPreviewResponse buildVerifiedExportPreview(LocalDate transactionMonth) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");

        List<TAccountsPayableSummary> dbRows = payableRepository.findVerifiedForMfExport(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
        List<TPaymentMfAuxRow> auxRows = auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);

        // supplier_no 単位に集約。税率別複数行は verified_amount を SUM する
        // （手動 verify で税率別に異なる値が入り得るため、代表1行採用では乖離する）。
        Map<Integer, List<TAccountsPayableSummary>> bySupplierNo = dbRows.stream()
                .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
        }

        int payableCount = 0;
        long payableTotal = 0L;
        List<String> skipped = new ArrayList<>();
        for (List<TAccountsPayableSummary> group : bySupplierNo.values()) {
            TAccountsPayableSummary s = group.get(0);
            MPaymentMfRule rule = s.getSupplierCode() == null ? null : byCode.get(s.getSupplierCode());
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) {
                skipped.add(s.getSupplierCode() + " (supplier_no=" + s.getSupplierNo()
                        + (rule == null ? " ルール未登録" : " 非PAYABLE=" + rule.getRuleKind()) + ")");
                continue;
            }
            long amount = sumVerifiedAmountForGroup(group);
            if (amount == 0L) continue;
            payableCount++;
            payableTotal += amount;
        }

        // 補助行の内訳を (transferDate, ruleKind) でグルーピング
        Map<String, VerifiedExportPreviewResponse.AuxBreakdownItem> breakdown = new LinkedHashMap<>();
        Set<LocalDate> seenTransferDates = new java.util.LinkedHashSet<>();
        for (TPaymentMfAuxRow aux : auxRows) {
            seenTransferDates.add(aux.getTransferDate());
            String key = aux.getTransferDate() + "|" + aux.getRuleKind();
            VerifiedExportPreviewResponse.AuxBreakdownItem item =
                    breakdown.computeIfAbsent(key, k -> VerifiedExportPreviewResponse.AuxBreakdownItem.builder()
                            .transferDate(aux.getTransferDate())
                            .ruleKind(aux.getRuleKind())
                            .count(0)
                            .totalAmount(0L)
                            .build());
            item.setCount(item.getCount() + 1);
            item.setTotalAmount(item.getTotalAmount() + (aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount())));
        }

        // 警告: 取引月の翌月に 5日払い相当 / 20日払い相当の送金分が揃っているか確認。
        // 土日祝による振替で 5日 が 4日/6日/7日、20日 が 19日/21日 等に前後するため、
        // 翌月の前半(1日〜境界日-1)を「5日払い相当」、後半(境界日〜末日)を「20日払い相当」として判定する。
        // 境界日は FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF で定数化。
        List<String> warnings = new ArrayList<>();
        LocalDate nextMonthStart = transactionMonth.plusMonths(1).withDayOfMonth(1);
        LocalDate nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth());
        LocalDate midMonth = nextMonthStart.withDayOfMonth(FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF);

        boolean hasFirstHalf = seenTransferDates.stream()
                .anyMatch(d -> !d.isBefore(nextMonthStart) && d.isBefore(midMonth));
        boolean hasSecondHalf = seenTransferDates.stream()
                .anyMatch(d -> !d.isBefore(midMonth) && !d.isAfter(nextMonthEnd));

        if (!hasFirstHalf) {
            warnings.add("5日払い相当 Excel（" + nextMonthStart + " 〜 " + midMonth.minusDays(1)
                    + " の送金分）の補助行が見つかりません。"
                    + "「振込明細で一括検証」から 5日払い Excel をアップロードしてください");
        }
        if (!hasSecondHalf) {
            warnings.add("20日払い相当 Excel（" + midMonth + " 〜 " + nextMonthEnd
                    + " の送金分）の補助行が見つかりません。"
                    + "「振込明細で一括検証」から 20日払い Excel をアップロードしてください");
        }

        return VerifiedExportPreviewResponse.builder()
                .transactionMonth(transactionMonth)
                .payableCount(payableCount)
                .payableTotalAmount(payableTotal)
                .auxBreakdown(new ArrayList<>(breakdown.values()))
                .warnings(warnings)
                .skippedSuppliers(skipped)
                .build();
    }

    /** 補助行一覧を取引月指定で返す（タブ表示用）。 */
    @Transactional(readOnly = true)
    public List<PaymentMfAuxRowResponse> listAuxRows(LocalDate transactionMonth) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");
        return auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth)
                .stream()
                .map(PaymentMfAuxRowResponse::from)
                .toList();
    }

    /**
     * 履歴保存は別トランザクションにして、履歴保存失敗が CSV 生成本体の結果に影響しないようにする。
     * 例外は握り潰さず呼び元へ伝播し、呼び元でログ＋ユーザ警告に変換する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveVerifiedExportHistory(LocalDate transactionMonth,
                                             int rowCount, long totalAmount, byte[] csv, Integer userNo) {
        String yyyymmdd = transactionMonth.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String csvFile = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
        String source = "verified-export_" + transactionMonth;
        TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                // 履歴の transferDate カラムは既存フロー互換で締め日をそのまま入れる
                // (送金日は CSV に含まれず、検証済み出力では意味を持たないため)
                .transferDate(transactionMonth)
                .sourceFilename(source)
                .csvFilename(csvFile)
                .rowCount(rowCount)
                .totalAmount(totalAmount)
                .matchedCount(rowCount)
                .diffCount(0)
                .unmatchedCount(0)
                .csvBody(csv)
                .addDateTime(LocalDateTime.now())
                .addUserNo(userNo)
                .build();
        historyRepository.save(h);
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
        // PAYABLE ルールがマッチしたが payment_supplier_code が未設定の送り先。
        // 検証済みCSV出力で CSV 除外されるため、一括検証前に補完を促す。
        Set<String> rulesMissingCode = new java.util.LinkedHashSet<>();
        int matched = 0, diff = 0, unmatched = 0, errors = 0;
        long totalAmount = 0L;
        long preTotalAmount = 0L;   // 合計行前（PAYABLE+EXPENSE）の請求額合計
        long directPurchaseTotal = 0L; // 合計行後（DIRECT_PURCHASE）の請求額合計

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
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
            if (e.afterTotal) {
                directPurchaseTotal += e.amount;
            } else {
                preTotalAmount += e.amount;
            }
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

            // 突合用コードは Excel 側を優先、無ければルール側にバックフィル済みのコードを使用
            String reconcileCode = e.supplierCode != null ? e.supplierCode
                    : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                            ? rule.getPaymentSupplierCode() : null);
            // PAYABLE ルール自体に payment_supplier_code が未設定なら、検証済みCSV出力 時に
            // t_accounts_payable_summary.supplier_code でルールを逆引き出来ず CSV 除外される。
            // 一括検証前に「支払先コード自動補完」でマスタ整備を促すため一覧化する。
            boolean ruleHasCode = rule.getPaymentSupplierCode() != null
                    && !rule.getPaymentSupplierCode().isBlank();
            if ("PAYABLE".equals(rule.getRuleKind()) && !ruleHasCode) {
                String code = e.supplierCode != null ? e.supplierCode : "code未設定";
                rulesMissingCode.add(code + " " + e.sourceName);
            }
            if ("PAYABLE".equals(rule.getRuleKind()) && txMonth != null && reconcileCode != null) {
                ReconcileResult rr = reconcile(reconcileCode, txMonth, e.amount);
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

        // 整合性チェック (1 円も許容しない)
        long summaryFee = cached.getSummarySourceFee() == null ? 0L : cached.getSummarySourceFee();
        long summaryEarly = cached.getSummaryEarlyPayment() == null ? 0L : cached.getSummaryEarlyPayment();
        long summaryTransfer = cached.getSummaryTransferAmount() == null ? 0L : cached.getSummaryTransferAmount();
        long summaryInvoice = cached.getSummaryInvoiceTotal() == null ? 0L : cached.getSummaryInvoiceTotal();

        // チェック1: Excel 合計行の列間整合 — C(請求額) - F(振込手数料) - H(早払) == E(振込金額)
        long expectedTransfer = summaryInvoice - summaryFee - summaryEarly;
        long excelDifference = summaryTransfer - expectedTransfer;
        boolean excelMatched = excelDifference == 0;

        // チェック2: 明細行の読取り整合 — sum(合計行前 明細 請求額) == C(合計行 請求額)
        long readDifference = preTotalAmount - summaryInvoice;
        boolean readMatched = readDifference == 0;

        PaymentMfPreviewResponse.AmountReconciliation recon =
                PaymentMfPreviewResponse.AmountReconciliation.builder()
                        .summaryInvoiceTotal(summaryInvoice)
                        .summaryFee(summaryFee)
                        .summaryEarly(summaryEarly)
                        .summaryTransferAmount(summaryTransfer)
                        .expectedTransferAmount(expectedTransfer)
                        .excelDifference(excelDifference)
                        .excelMatched(excelMatched)
                        .preTotalInvoiceSum(preTotalAmount)
                        .readDifference(readDifference)
                        .readMatched(readMatched)
                        .directPurchaseTotal(directPurchaseTotal)
                        .build();

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
                .rulesMissingSupplierCode(new ArrayList<>(rulesMissingCode))
                .amountReconciliation(recon)
                .build();
    }

    private String renderSummary(MPaymentMfRule rule, PaymentMfExcelParser.ParsedEntry e) {
        return renderSummary(rule, e.sourceName);
    }

    private String renderSummary(MPaymentMfRule rule, String sourceName) {
        String tpl = rule.getSummaryTemplate();
        if (tpl == null || tpl.isEmpty()) return sourceName;
        String sub = rule.getDebitSubAccount() != null ? rule.getDebitSubAccount() : sourceName;
        return tpl.replace("{sub_account}", sub)
                  .replace("{source_name}", sourceName);
    }

    /**
     * 振込明細 Excel 上の「仕入コード」(2〜3桁の数値) を、DB の
     * {@code m_payment_supplier.payment_supplier_code}（6桁ゼロ埋め）形式に正規化する。
     *
     * <p>運用ルール:
     * <ul>
     *   <li>Excel の仕入コードは "親支払先番号" のみを示し、DB 側は `コード×100` を 6桁ゼロ埋めで保持する。
     *       1〜4桁の数値は×100してから6桁ゼロ埋め
     *       （例: Excel "8" → "000800", "81" → "008100", "101" → "010100", "1234" → "123400"）。</li>
     *   <li>5桁以上の値は既に DB 形式（支払先コード形式）とみなし、×100せず6桁ゼロ埋めのみ行う
     *       （例: Excel "12345" → "012345"）。</li>
     *   <li>境界ケース:
     *     <ul>
     *       <li>先頭ゼロ付き入力（例: "0081"）は {@code Long.parseLong} で 81 になり、
     *           4桁以下として×100 される（"008100"）。つまり桁数判定は「数値としての長さ」ではなく
     *           「入力文字列の長さ」で行う点に注意。</li>
     *       <li>1桁入力は×100扱い（"0" のみは 0 → "000000" となり下流で不一致となる）。</li>
     *     </ul>
     *   </li>
     *   <li>非数字・空文字は trim した元文字列を返却（下流で不一致としてログされる）。</li>
     * </ul>
     * <p>パッケージプライベート: 単体テスト向け。外部からは呼ばない。
     */
    static String normalizePaymentSupplierCode(String raw) {
        if (raw == null) return null;
        String digits = raw.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) return digits;
        try {
            long n = Long.parseLong(digits);
            if (digits.length() <= 4) n = n * 100L;
            String s = Long.toString(n);
            if (s.length() >= 6) return s;
            return "0".repeat(6 - s.length()) + s;
        } catch (NumberFormatException ex) {
            return digits;
        }
    }

    /**
     * 送金日 → 対応する買掛金の取引月（締め日）を決定する。
     * <p>運用実態: 5日送金・20日送金ともに「前月20日締め」の買掛金を支払う。
     * <ul>
     *   <li>5日送金  = 前月20日締め残高の主精算</li>
     *   <li>20日送金 = 同じ前月20日締め残高の2回目精算（早払い／残金）。
     *       20日は「当月の締め日」でもあるが、その日の送金は当日時点で既に確定している
     *       前月締め残高に対して行うのが運用。当月分(2026-04-20締め)は翌月5日送金で支払う。</li>
     * </ul>
     * <p>※ design-payment-mf-import.md §5.1 の「20日→当月20日締め」記述は運用実態と異なるため
     * 次回設計書更新で是正すること。
     * <p>例) 2026/02/05 → 2026-01-20
     *       2026/02/20 → 2026-01-20
     *       2026/04/20 → 2026-03-20
     */
    LocalDate deriveTransactionMonth(LocalDate transferDate) {
        return transferDate.minusMonths(1).withDayOfMonth(20);
    }

    private static class ReconcileResult {
        String status; Long payableAmount; Long diff; Integer supplierNo;
    }

    private ReconcileResult reconcile(String supplierCode, LocalDate txMonth, Long invoiceAmount) {
        ReconcileResult r = new ReconcileResult();
        List<TAccountsPayableSummary> list = payableRepository
                .findByShopNoAndSupplierCodeAndTransactionMonth(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, supplierCode, txMonth);
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
            if (v != null) payable += toLongFloor(v);
            if (supplierNo == null) supplierNo = s.getSupplierNo();
        }
        r.payableAmount = payable;
        r.diff = payable - invoiceAmount;
        r.supplierNo = supplierNo;
        r.status = Math.abs(r.diff) <= FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE_LONG
                ? "MATCHED" : "DIFF";
        return r;
    }

    // Excel シート選択・解析は {@link PaymentMfExcelParser} に分離。


    // ===========================================================
    // CSV generation
    // ===========================================================

    /**
     * CSV バイト列を生成する。
     * <p>第2引数 {@code transactionDate} は CSV「取引日」列に入れる値で、小田光の
     * 締め日(= 前月20日, transactionMonth)を渡す運用。送金日ではない点に注意
     * （送金日は MF の銀行データ連携側で自動付与される）。
     */
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
                    .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
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

    /**
     * enforceCacheLimit と cache.put を 1 つの synchronized ブロックに入れ、
     * 「サイズ閾値チェック直後に別スレッドが put し上限を超える」race を排除する。
     */
    private synchronized void putCacheAtomically(String uploadId, CachedUpload cached) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
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
        cache.put(uploadId, cached);
    }

    // Excel セル読み出し・文字列正規化は {@link PaymentMfCellReader} に分離。

    /** 他クラスから参照されているためシム関数として残す。実装は {@link PaymentMfCellReader#normalize}。 */
    static String normalize(String s) {
        return PaymentMfCellReader.normalize(s);
    }

    // ===========================================================
    // DTOs (internal)
    // ===========================================================

    @Data
    @Builder
    static class CachedUpload {
        private List<PaymentMfExcelParser.ParsedEntry> entries;
        private LocalDate transferDate;
        private Long summarySourceFee;
        private Long summaryEarlyPayment;
        /** 合計行 振込金額列 の値（参考情報）。 */
        private Long summaryTransferAmount;
        /** 合計行 請求額列 の値（明細読取り整合性チェック用）。 */
        private Long summaryInvoiceTotal;
        private String fileName;
        private long expiresAt;
    }
}
