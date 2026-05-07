package jp.co.oda32.domain.service.finance;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.audit.FinanceAuditWriter;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.AppliedWarning;
import jp.co.oda32.dto.finance.paymentmf.DuplicateWarning;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
import jp.co.oda32.exception.FinanceBusinessException;
import jp.co.oda32.exception.FinanceInternalException;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    /**
     * G2-M8: OFFSET 副行貸方科目マスタ ({@code m_offset_journal_rule}) の Repository。
     *
     * <p>従来 {@code "仕入値引・戻し高" / "物販事業部" / "課税仕入-返還等 10%"} と
     * ハードコードされていた OFFSET 副行貸方を本テーブルから lookup することで、
     * 税理士確認後に admin が UI 経由で値を書き換えられるようにした。
     * V041 の seed で shop_no=1 のデフォルト値 (= 旧ハードコード値と同一) を投入済。
     */
    private final MOffsetJournalRuleRepository offsetJournalRuleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
     * Spring AOP プロキシ経由になるようにするため。{@code @Lazy} は循環依存回避。
     */
    @Autowired
    @Lazy
    private PaymentMfImportService self;

    /**
     * G2-M2 (2026-05-06): per-supplier 1 円不一致を {@code force=true} で許容して反映した際、
     * 既存 {@code @AuditLog} aspect の after snapshot 行とは別に、違反詳細を {@code reason} 列に
     * 持つ補足 audit 行を 1 件追加する。AOP 拡張せずに済むよう field injection で別 Bean を持つ。
     * <p>{@code @Lazy} は AOP / proxy のブートストラップ循環回避用 (writer 自体は内部で
     * {@code FinanceAuditLogRepository} を使うのみで本サービスへの依存は無いが、保守的に Lazy)。
     */
    @Autowired(required = false)
    @Lazy
    private FinanceAuditWriter financeAuditWriter;

    /** {@code force=true} 補足 audit 行の reason 値を組み立てる際の supplier mismatch 詳細表示上限。 */
    private static final int FORCE_AUDIT_MISMATCH_DETAIL_LIMIT = 50;

    // 差額一致閾値は FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE(_LONG) に集約。
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 10000;
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // 取引月単位の advisory lock キー計算 / 取得は {@link FinancePayableLock} に共通化
    // (G2-M2 Codex Major #2)。3 経路 (BULK / MANUAL / MF_OVERRIDE) で同一 util を使う。

    // CSV 生成ロジックは {@link PaymentMfCsvWriter} に分離（ステートレスユーティリティ）。

    // Excel 読み取り（selectSheet / parseSheet / メタ行判定 など）は
    // {@link PaymentMfExcelParser} に分離。ParsedEntry / ParsedExcel / SectionSummary も同クラスに移動。

    /**
     * アップロード済み Excel のパース結果をインメモリ保持する。
     * <p><b>node-local</b>: 複数 JVM 起動（HA 構成）では preview と convert/applyVerification が
     * 別インスタンスに到達すると 404 になる。本アプリは single-instance 前提の設計
     * （{@code MfOauthStateStore} と同じスコープ）。マルチ化するときは Redis 等に寄せること (B-W9)。
     */
    private final Map<String, CachedUpload> cache = new ConcurrentHashMap<>();

    public PaymentMfImportService(MPaymentMfRuleRepository ruleRepository,
                                  TPaymentMfImportHistoryRepository historyRepository,
                                  TAccountsPayableSummaryRepository payableRepository,
                                  TAccountsPayableSummaryService payableService,
                                  TPaymentMfAuxRowRepository auxRowRepository,
                                  MOffsetJournalRuleRepository offsetJournalRuleRepository) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
        this.payableRepository = payableRepository;
        this.payableService = payableService;
        this.auxRowRepository = auxRowRepository;
        this.offsetJournalRuleRepository = offsetJournalRuleRepository;
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
        // P1-08 Phase 1: ファイルハッシュを取得元バイト列から計算 (cache に保存)。
        // file.getBytes() は MAX_UPLOAD_BYTES (20MB) で上限済 (validateFile)。
        byte[] uploadedBytes = file.getBytes();
        String fileHash = computeSha256Hex(uploadedBytes);
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(uploadedBytes))) {
            Sheet sheet = PaymentMfExcelParser.selectSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("振込明細シート（支払い明細/振込明細）が見つかりません");
            }
            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);
            if (parsed.entries.size() > MAX_DATA_ROWS) {
                throw new IllegalArgumentException("データ行数上限を超過しています: " + parsed.entries.size());
            }

            String uploadId = UUID.randomUUID().toString();
            CachedUpload cached = CachedUpload.builder()
                    .entries(parsed.entries)
                    .transferDate(parsed.transferDate)
                    .summaries(parsed.summaries)
                    .fileName(PaymentMfCellReader.sanitize(file.getOriginalFilename()))
                    .sourceFileHash(fileHash)
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
     *
     * <p>G2-M2 (2026-05-06): per-supplier 1 円不一致 ({@code perSupplierMismatches}) が
     * 1 件でもあれば 422 でブロックする。業務的には preview 画面で違反を確認してから
     * Excel を修正 → 再アップロードする運用なので、CSV ダウンロード経路には force 上書きを設けない。
     */
    public byte[] convert(String uploadId, Integer userNo) {
        CachedUpload cached = getCached(uploadId);
        PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
        if (preview.getErrorCount() > 0) {
            // T5: 業務メッセージ (ユーザーがマスタ登録すれば解決) なので FinanceBusinessException を使う。
            // 旧 IllegalStateException → FinanceExceptionHandler.handleIllegalState で 422 + 汎用化されてしまい
            // フロントに件数情報が届かない問題があった (Cluster C M1)。
            throw new FinanceBusinessException(
                    "未登録の送り先があります（" + preview.getErrorCount() + "件）。マスタ登録後に再試行してください");
        }
        // G2-M2: per-supplier 1 円不一致は CSV 出力経路では強制上書き不可 (Excel 修正が正しい運用)。
        List<String> mismatches = perSupplierMismatchesOf(preview);
        if (!mismatches.isEmpty()) {
            throw new FinanceBusinessException(
                    "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                            + "プレビュー画面で詳細を確認し、Excel を修正してから再アップロードしてください",
                    FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
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
     * 旧 API 後方互換用 ({@code force=false} 固定で {@link #applyVerification(String, Integer, boolean, String)} を呼ぶ)。
     * <p>テスト・既存 batch 経路から既にこの 2 引数版が直接呼ばれているため、シグネチャは維持する。
     * 本メソッド経由でも force false 経路で per-supplier 1 円不一致は 422 ブロックとなる。
     *
     * @deprecated G2-M2 (2026-05-06) 以降、新コードは {@link #applyVerification(String, Integer, boolean, String)}
     *             を使い、{@code force} の意図と承認理由を明示すること。
     */
    @Deprecated
    public VerifyResult applyVerification(String uploadId, Integer userNo) {
        return applyVerification(uploadId, userNo, false, null);
    }

    /**
     * 旧 3-引数 API 後方互換用 (Codex Major #4 で {@code forceReason} を追加した 4 引数版を呼ぶ)。
     * 既存テストと旧 client が 3 引数で呼んでいるため、シグネチャは維持する。
     * <p>{@code force=true} で本オーバーロードを呼ぶと {@code forceReason} が無いため
     * {@link FinanceBusinessException} (FORCE_REASON_REQUIRED) が投げられる点に注意。
     *
     * @deprecated G2-M4 fix (2026-05-06) 以降、新コードは
     *             {@link #applyVerification(String, Integer, boolean, String)} を使い、
     *             {@code force=true} 時は forceReason も渡すこと。
     */
    @Deprecated
    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force) {
        return applyVerification(uploadId, userNo, force, null);
    }

    /**
     * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
     * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
     *
     * <p>並列呼び出しでの上書き競合を防ぐため、同一 (shop_no, transaction_month) に対しては
     * PostgreSQL advisory lock で直列化する。5日払い Excel と 20日払い Excel が別プロセス/
     * 別 UI から同時適用された場合でも、後着は先行 tx のコミット完了後に実行される。
     *
     * <p>G2-M2 (2026-05-06): {@code force} パラメータ追加。
     * <ul>
     *   <li>{@code force=false} (推奨デフォルト): per-supplier 1 円整合性違反
     *       ({@code AmountReconciliation#perSupplierMismatches}) が 1 件でもあれば
     *       {@link FinanceBusinessException} (422) でブロックする。
     *       業務的には preview で違反一覧を確認 → Excel を修正 → 再アップロード が正しい運用。</li>
     *   <li>{@code force=true}: 違反を許容して反映する。
     *       {@code finance_audit_log.reason} に {@code FORCE_APPLIED: per-supplier mismatches=...}
     *       で違反詳細を補足記録し、後追い監査を可能にする。</li>
     * </ul>
     *
     * <p>Codex Major #4 (2026-05-06): {@code forceReason} 必須化。
     * <ul>
     *   <li>{@code force=true} かつ {@code forceReason} が null / 空文字 →
     *       {@link FinanceBusinessException} (FORCE_REASON_REQUIRED, 400) で拒否。</li>
     *   <li>{@code force=false} の場合 {@code forceReason} は無視される。</li>
     * </ul>
     * 二段認可は実装スコープ過大のため運用 runbook (= 最低 2 名で内容確認) にて担保し、
     * 実装は forceReason 必須化のみで「誰が」「なぜ」を audit に残せるようにする。
     */
    @Transactional
    @AuditLog(table = "t_accounts_payable_summary", operation = "payment_mf_apply",
            pkExpression = "{'uploadId': #a0, 'userNo': #a1, 'force': #a2}",
            captureArgsAsAfter = true, captureReturnAsAfter = true)
    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force, String forceReason) {
        CachedUpload cached = getCached(uploadId);
        if (cached.getTransferDate() == null) {
            throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
        }
        // Codex Major #4: force=true 時の forceReason 必須化 (advisory lock 取得前に bail-out)。
        // null / 空文字は不可。空白のみ ("   ") も拒否し、運用上意味のある理由を強制する。
        if (force && (forceReason == null || forceReason.isBlank())) {
            throw new FinanceBusinessException(
                    "force=true 指定時は forceReason (承認理由) が必須です。"
                            + "承認者・確認者・業務理由を含めて入力してください",
                    FinanceConstants.ERROR_CODE_FORCE_REASON_REQUIRED);
        }
        LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
        acquireAdvisoryLock(txMonth);

        // G2-M2: per-supplier 1 円整合性違反のサーバー側ブロック。
        // preview を一度だけ事前構築し、ブロック判定 (force) と aux 保存の両方で使い回す。
        // (このタイミングなら DB ロック取得後で他 tx の影響を受けにくい)。
        PaymentMfPreviewResponse blockingPreview = buildPreview(uploadId, cached);
        List<String> mismatches = perSupplierMismatchesOf(blockingPreview);
        if (!mismatches.isEmpty() && !force) {
            // 422 + コード PER_SUPPLIER_MISMATCH を返す。client 側で「force=true で再実行」UI 分岐に使う。
            // 件数のみ返却し、supplier 名は preview レスポンスから別途取得させる
            // (例外メッセージに長い detail を載せると i18n / log noise の原因になる)。
            throw new FinanceBusinessException(
                    "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                            + "プレビュー画面で詳細を確認の上、強制実行する場合は force=true を指定してください",
                    FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
        }

        // G2-M10 (V040): note 接頭辞は UI 表示用にのみ利用 (BULK/MANUAL 判定には verification_source を使う)。
        @SuppressWarnings("deprecation")
        String bulkPrefix = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX;
        String note = bulkPrefix + cached.getFileName() + " " + cached.getTransferDate();

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        // 事前パス: 当該 Excel に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
        // 5日払い (PAYMENT_5TH) のみが PAYABLE 突合対象 (20日払いセクションは DIRECT_PURCHASE 自動降格)。
        Set<String> codesToReconcile = new java.util.LinkedHashSet<>();
        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            if (e.section == PaymentMfSection.PAYMENT_20TH) continue;
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

        int matched = 0, diff = 0, notFound = 0, skipped = 0, skippedManuallyVerified = 0;
        List<String> unmatchedSuppliers = new ArrayList<>();
        BigDecimal matchThreshold = FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE;

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            // PAYABLE行のみ対象（20日払いセクションは DIRECT_PURCHASE 自動降格扱いで対象外）
            if (e.section == PaymentMfSection.PAYMENT_20TH) { skipped++; continue; }
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

            // P1-08 Q3-(ii) / Cluster D SF-02 同パターン (G2-M10, V040 で source 列ベースに改修):
            //   `verified_manually=true` かつ <b>UI 手入力 verify (MANUAL_VERIFICATION)</b> 由来の行は
            //   再 upload で上書きしない。BULK_VERIFICATION (再 upload) や MF_OVERRIDE は上書き対象。
            //   税率別複数行のうち 1 行でも MANUAL があれば supplier 全体を保護対象とする
            //   (税率別 verified_amount/note を分割保持していないため、混在状態を作らないこと優先)。
            //
            //   旧実装は verification_note 接頭辞文字列 (VERIFICATION_NOTE_BULK_PREFIX) で BULK/MANUAL を
            //   推定していたが、ユーザが偶然 "振込明細検証 " で始まる note を手入力すると保護が外れる
            //   リスクがあった。V040 で verification_source 列を追加し、書込経路を明示記録する運用に切替。
            boolean anyManuallyLocked = isAnyManuallyLocked(list);
            if (anyManuallyLocked) {
                skippedManuallyVerified++;
                log.info("verified_manually=true (単一 verify) 行をスキップ: supplier={}({}) txMonth={}",
                        e.sourceName, reconcileCode, txMonth);
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
            // V026: 税抜側 (verified_amount_tax_excluded) も税率別に逆算して書き込む
            // → MF CSV 出力の「仕入高」金額が仕入先請求書と一致する。
            // auto_adjusted_amount に自動調整額 (verified - 元の自社計算) を記録 (監査証跡)。
            BigDecimal autoAdjusted = isMatched ? invoice.subtract(payable) : BigDecimal.ZERO;
            String adjustNote = isMatched && autoAdjusted.signum() != 0
                    ? note + " | 自動調整: 元 ¥" + payable + " → ¥" + invoice
                      + " (" + (autoAdjusted.signum() > 0 ? "+" : "") + "¥" + autoAdjusted + ")"
                    : note;
            for (TAccountsPayableSummary s : list) {
                s.setVerificationResult(isMatched ? 1 : 0);
                s.setPaymentDifference(difference);
                s.setVerifiedManually(true);
                s.setMfExportEnabled(isMatched);
                s.setVerificationNote(adjustNote);
                s.setVerifiedAmount(invoice);
                // V026: 税抜 verified を税率別に逆算 (単一税率前提。複数税率の仕入先は手動調整で対応)。
                BigDecimal taxRate = s.getTaxRate() != null ? s.getTaxRate() : BigDecimal.TEN;
                BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate);
                BigDecimal invoiceTaxExcl = invoice.multiply(BigDecimal.valueOf(100))
                        .divide(divisor, 0, java.math.RoundingMode.DOWN);
                s.setVerifiedAmountTaxExcluded(invoiceTaxExcl);
                s.setAutoAdjustedAmount(autoAdjusted);
                // 5日払いセクション (PAYMENT_5TH) の PAYABLE のみここに到達する。
                // Excel の送金日を CSV 取引日として記録する。
                s.setMfTransferDate(cached.getTransferDate());
                // G2-M1/M10 (V040): 書込経路を明示記録 (read 側 sumVerifiedAmountForGroup と
                // 再 upload 保護判定で参照される)。
                s.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_BULK);
                payableService.save(s);
            }
        }

        // 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
        // G2-M2: ブロック判定で構築した blockingPreview をそのまま使い回す
        // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
        PaymentMfPreviewResponse preview = blockingPreview;
        saveAuxRowsForVerification(cached, preview, txMonth, userNo);

        // P1-08: 確定済マークを history に永続化 (preview L2 警告の根拠データ)。
        saveAppliedHistory(cached, preview, userNo);

        // G2-M2: force=true で per-supplier 不一致を許容して反映した場合、
        // 補足 audit 行を追加して reason に違反詳細を残す。
        // 既存 @AuditLog aspect が記録する after snapshot 行とは別 row として書く
        // (AOP 拡張せずに済む & 後で reason 列で grep できる)。
        // Codex Major #4: forceReason (承認者・確認者・業務理由) も audit reason に含める。
        if (force && !mismatches.isEmpty()) {
            writeForceAppliedAuditRow(uploadId, userNo, cached, txMonth, mismatches, forceReason);
        }

        return VerifyResult.builder()
                .transferDate(cached.getTransferDate())
                .transactionMonth(txMonth)
                .matchedCount(matched)
                .diffCount(diff)
                .notFoundCount(notFound)
                .skippedCount(skipped)
                .skippedManuallyVerifiedCount(skippedManuallyVerified)
                .unmatchedSuppliers(unmatchedSuppliers)
                .build();
    }

    /**
     * G2-M2 (2026-05-06): {@code force=true} で per-supplier 1 円違反を許容して反映した時の補足 audit 行。
     * <p>{@link FinanceAuditWriter#write} を直接呼んで {@code reason} 列に件数 + 先頭
     * {@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件の詳細サマリを記録する (容量配慮)。
     * Codex Major #3 (2026-05-06): {@code force_mismatch_details} JSONB 列 (V043) に
     * <b>全件</b>を構造化保存し、reason との乖離を防ぐ。
     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を reason 末尾に
     * {@code reason="..."} 形式で連結する。null の場合は付加しない (旧 client 互換用、3 引数 deprecated 経路)。
     * <p>writer Bean が無い (test 環境など) や書込み失敗は本体反映を巻き戻さない (warn ログのみ)。
     */
    private void writeForceAppliedAuditRow(String uploadId, Integer userNo, CachedUpload cached,
                                           LocalDate txMonth, List<String> mismatches, String forceReason) {
        if (financeAuditWriter == null) {
            log.warn("FinanceAuditWriter Bean 不在のため force=true 補足 audit を skip: uploadId={}", uploadId);
            return;
        }
        try {
            String reason = buildForceAppliedReason(mismatches, forceReason);
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode pk = mapper.createObjectNode()
                    .put("uploadId", uploadId)
                    .put("userNo", userNo == null ? null : userNo.toString())
                    .put("force", "true")
                    .put("transferDate", cached.getTransferDate() == null ? null
                            : cached.getTransferDate().toString())
                    .put("transactionMonth", txMonth == null ? null : txMonth.toString())
                    .put("fileName", cached.getFileName());
            // Codex Major #3: 全件を JSONB 列に構造化保存 (reason 50 件切り詰めとの乖離防止)。
            com.fasterxml.jackson.databind.node.ArrayNode detailsArr = mapper.createArrayNode();
            for (String m : mismatches) {
                detailsArr.add(mapper.createObjectNode().put("line", m));
            }
            financeAuditWriter.write(
                    "t_accounts_payable_summary",
                    "payment_mf_apply_force",
                    userNo,
                    userNo == null ? "SYSTEM" : "USER",
                    pk,
                    null,
                    null,
                    reason,
                    null,
                    null,
                    detailsArr);
            log.warn("payment_mf_apply force=true で per-supplier 不一致 {} 件を許容: uploadId={} txMonth={}",
                    mismatches.size(), uploadId, txMonth);
        } catch (Exception ex) {
            log.error("force=true 補足 audit 行の書込に失敗 (本体反映は完了済): uploadId={} err={}",
                    uploadId, ex.toString());
        }
    }

    /**
     * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる (旧 1 引数版、後方互換用)。
     * <p>新規コードは {@link #buildForceAppliedReason(List, String)} で forceReason も渡すこと。
     * @deprecated Codex Major #4 (2026-05-06) 以降。
     */
    @Deprecated
    static String buildForceAppliedReason(List<String> mismatches) {
        return buildForceAppliedReason(mismatches, null);
    }

    /**
     * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる。
     * <p>形式 (forceReason あり): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...], reason=\"...\""}
     * <p>形式 (forceReason なし): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"}
     * <p>{@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件超過時は {@code "...(+M more)"} で打ち切り。
     * 全件は別途 {@code finance_audit_log.force_mismatch_details} JSONB 列に保存される (V043, Codex Major #3)。
     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を末尾に追記。
     * null / 空文字なら省略 (旧 deprecated 経路互換)。
     */
    static String buildForceAppliedReason(List<String> mismatches, String forceReason) {
        StringBuilder sb = new StringBuilder("FORCE_APPLIED: per-supplier mismatches count=");
        int total = mismatches == null ? 0 : mismatches.size();
        sb.append(total);
        if (total > 0) {
            int show = Math.min(total, FORCE_AUDIT_MISMATCH_DETAIL_LIMIT);
            sb.append(", details=[");
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(mismatches.get(i));
            }
            if (total > show) {
                sb.append(", ...(+").append(total - show).append(" more)");
            }
            sb.append(']');
        }
        if (forceReason != null && !forceReason.isBlank()) {
            // 二重引用符の入れ子を避けるため、value 側の " は ' に置換。改行は半角スペースに正規化
            // (audit log の grep 安定化)。
            String sanitized = forceReason.replace('"', '\'').replace('\n', ' ').replace('\r', ' ');
            sb.append(", reason=\"").append(sanitized).append('"');
        }
        return sb.toString();
    }

    /**
     * G2-M2: preview から per-supplier 1 円不一致リストを安全に取り出す。
     * {@code amountReconciliation} or {@code perSupplierMismatches} が null の場合は空リスト。
     *
     * <p>パッケージ可視 + non-static にしてテストから {@code Mockito.spy} で差し替え可能にする
     * (Excel fixture を mismatch 用に作るのが難しいため、テストフックとして残す)。
     */
    List<String> perSupplierMismatchesOf(PaymentMfPreviewResponse preview) {
        if (preview == null || preview.getAmountReconciliation() == null) return List.of();
        List<String> mm = preview.getAmountReconciliation().getPerSupplierMismatches();
        return mm == null ? List.of() : mm;
    }

    /**
     * P1-08: applyVerification 実行時に history 行を 1 件追加し、
     * {@code applied_at} / {@code applied_by_user_no} / {@code source_file_hash} を記録する。
     * これにより後続 preview で同 (shop, transferDate) の L2 警告 (確定済) が出る。
     *
     * <p>convert と異なり CSV bytes は持たない (このフローは MF CSV 出力ではなく買掛検証反映のため)。
     * csv_body は NULL、csv_filename は applied マーカーであることを示す名前にする。
     */
    private void saveAppliedHistory(CachedUpload cached, PaymentMfPreviewResponse preview, Integer userNo) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String yyyymmdd = cached.getTransferDate() == null ? "unknown"
                    : cached.getTransferDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                    .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                    .transferDate(cached.getTransferDate())
                    .sourceFilename(cached.getFileName())
                    .csvFilename("applied_" + yyyymmdd + ".marker")
                    .rowCount(preview.getTotalRows())
                    .totalAmount(preview.getTotalAmount())
                    .matchedCount(preview.getMatchedCount())
                    .diffCount(preview.getDiffCount())
                    .unmatchedCount(preview.getUnmatchedCount())
                    .csvBody(null)
                    .sourceFileHash(cached.getSourceFileHash())
                    .appliedAt(now)
                    .appliedByUserNo(userNo)
                    .addDateTime(now)
                    .addUserNo(userNo)
                    .build();
            historyRepository.save(h);
        } catch (Exception ex) {
            // history 保存失敗は本体検証結果に影響させない (verified_manually=true は既に永続化済)。
            log.error("applyVerification 履歴の保存に失敗 (検証反映は正常完了): file={}", cached.getFileName(), ex);
        }
    }

    /**
     * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
     * 直列化する。PostgreSQL の {@code pg_advisory_xact_lock} は現在のトランザクション終了時に
     * 自動解放されるため、解放漏れリスクが無い。
     *
     * <p>G2-M2 (Codex Major #2, 2026-05-06): キー計算と native query 発行を {@link FinancePayableLock}
     * に切り出し、{@link TAccountsPayableSummaryService#verify} / {@link ConsistencyReviewService#applyMfOverride}
     * の 3 書込経路で同一 lock を取れるようにした。shop_no は {@link FinanceConstants#ACCOUNTS_PAYABLE_SHOP_NO}
     * 固定 (買掛金管理は shop=1 のみ運用)。
     */
    private void acquireAdvisoryLock(LocalDate transactionMonth) {
        FinancePayableLock.acquire(entityManager,
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
    }

    /**
     * applyVerification 時に EXPENSE / SUMMARY / DIRECT_PURCHASE 主行および
     * {@code PAYABLE_xxx} / {@code DIRECT_PURCHASE_xxx} 副行を
     * {@code t_payment_mf_aux_row} に洗い替え保存する。
     * PAYABLE 主行は {@code t_accounts_payable_summary} 側で管理するためここでは対象外。
     * UNREGISTERED 行は CSV に出ないため保存しない。
     *
     * <p>C2 (2026-05-06) 修正: 従来 {@code PAYABLE_xxx} / {@code DIRECT_PURCHASE_xxx} 副行を
     * skip していたため、exportVerifiedCsv (DB-only 経路) で副行が消失していた。本修正で副行も保存し、
     * V038 で {@code chk_payment_mf_aux_rule_kind} 制約を拡張した。
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
            // C2 (2026-05-06):
            //   - PAYABLE 主行: t_accounts_payable_summary 由来 → aux 保存対象外 (重複排除)。
            //   - PAYABLE_* 副行 (PAYABLE_FEE/DISCOUNT/EARLY/OFFSET): aux 保存対象。
            //     exportVerifiedCsv で副行を再構築するため (従来は消失していた)。
            //   - DIRECT_PURCHASE 主行・副行: 共に aux 保存対象。
            //   - EXPENSE / SUMMARY 主行: 従来どおり aux 保存対象。
            //   - UNREGISTERED 行: CSV に出ないため保存しない。
            // V038 で chk_payment_mf_aux_rule_kind 制約を拡張済。
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
        /**
         * P1-08 Q3-(ii): verified_manually=true (単一 verify 由来) で保護されスキップされた supplier 数。
         * &gt;0 のとき UI で「N 件は手動確定保護のため上書きしませんでした」を表示する。
         */
        private int skippedManuallyVerifiedCount;
        private List<String> unmatchedSuppliers;
    }

    public byte[] getHistoryCsv(Integer id) {
        return historyRepository.findById(id)
                .map(TPaymentMfImportHistory::getCsvBody)
                .orElseThrow(() -> new IllegalArgumentException("履歴が見つかりません: " + id));
    }

    public java.util.Optional<LocalDate> getHistoryTransferDate(Integer id) {
        return historyRepository.findById(id)
                .map(TPaymentMfImportHistory::getTransferDate);
    }

    /**
     * SF-C20: ダウンロード時のファイル名生成用に、指定 uploadId の送金日を返す。
     * 期限切れ・未登録の場合は {@link IllegalArgumentException}。
     */
    public LocalDate getCachedTransferDate(String uploadId) {
        return getCached(uploadId).getTransferDate();
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
     * 同一 supplier_no に属する税率別行群から、MF出力用の合計額を算出する (G2-M1, V040 で source 列ベースに改修)。
     * <p>判定は {@code verification_source} 列で行う:
     * <ul>
     *   <li><b>全行 BULK_VERIFICATION</b>: 振込明細一括検証で全税率行に同値の集約値が冗長保持されるため、
     *       代表 1 行の値を採用する (SUM すると件数倍の重複になる)。
     *       不変条件として全行同値のはずだが、念のため per-row 値を比較し、不一致なら
     *       <b>{@link FinanceInternalException} を throw して fail-closed</b> する
     *       (Codex Critical #1, 2026-05-06)。</li>
     *   <li><b>1 行でも MANUAL_VERIFICATION / MF_OVERRIDE / NULL が混在</b>: 税率別に異なる値が入りうるため SUM。
     *       MANUAL は単一 PK 更新、MF_OVERRIDE は税率別按分で書込まれているため、SUM が正しい集約値となる。</li>
     * </ul>
     * <p>旧実装は「全行 verifiedAmount 一致 → 代表値、不一致 → SUM」と金額パターンで推定していたが、
     * (a) MANUAL で偶然全行同値の場合に過少計上、(b) BULK 後の単行修正で過大計上のリスクがあった。
     * V040 で書込経路を {@code verification_source} 列に明示記録する運用に切替。
     * <p>verifiedAmount が null の行は taxIncludedAmountChange にフォールバック。
     *
     * <p><b>Codex Critical #1 (2026-05-06)</b>: 旧版は BULK 不一致時に WARN ログ + SUM フォールバックとして
     * いたが、SUM すると税率行数分に金額が膨らむ (= 過大計上で MF CSV に流出) ため、運用上は隔離すべき
     * 異常として {@link FinanceInternalException} を throw する。client には 422 で「内部エラー」を返し、
     * admin が DB 状態を点検 + 修復するまで CSV 生成を停止させる。
     * 関連 runbook: {@code claudedocs/runbook-payment-mf-bulk-invariant-violation.md}
     */
    private static long sumVerifiedAmountForGroup(List<TAccountsPayableSummary> group) {
        if (group.isEmpty()) return 0L;

        List<Long> perRow = new ArrayList<>(group.size());
        for (TAccountsPayableSummary s : group) {
            BigDecimal v = s.getVerifiedAmount() != null ? s.getVerifiedAmount()
                    : s.getTaxIncludedAmountChange();
            perRow.add(v == null ? 0L : toLongFloor(v));
        }

        // 全行 BULK_VERIFICATION 由来かを source 列で判定 (G2-M1)。
        // null 行 (未検証) や MANUAL / MF_OVERRIDE が 1 行でも混じれば SUM。
        boolean allBulk = group.stream()
                .allMatch(s -> FinanceConstants.VERIFICATION_SOURCE_BULK.equals(s.getVerificationSource()));
        if (allBulk) {
            long first = perRow.get(0);
            boolean allSame = perRow.stream().allMatch(x -> x == first);
            if (allSame) {
                // 正常: 集約値冗長保持
                return first;
            }
            // 異常: BULK_VERIFICATION の不変条件 (全行同値) が崩れている。
            // 旧版は SUM フォールバックしていたが、SUM は税率行数分の過大計上になり MF CSV 出力に流出するため、
            // Codex Critical #1 (2026-05-06) で fail-closed (例外 throw) に変更。
            // FinanceInternalException → FinanceExceptionHandler が 422 + 汎用メッセージで応答し、
            // CSV 生成 / verified export を停止させる。admin は runbook に従い DB 状態を点検して修復する。
            Integer supplierNo = group.get(0).getSupplierNo();
            LocalDate txMonth = group.get(0).getTransactionMonth();
            throw new FinanceInternalException(
                    "BULK_VERIFICATION 不変条件違反 (全税率行同値想定だが不一致): supplier_no="
                            + supplierNo + " txMonth=" + txMonth + " perRow=" + perRow);
        }

        // MANUAL / MF_OVERRIDE / NULL 混在: 税率別 SUM (本来の集約値)
        return perRow.stream().mapToLong(Long::longValue).sum();
    }

    /** P1-09 テスト用: {@link #sumVerifiedAmountForGroup} を package 外から呼ぶためのフック。 */
    static long sumVerifiedAmountForGroupForTest(List<TAccountsPayableSummary> group) {
        return sumVerifiedAmountForGroup(group);
    }

    /**
     * G2-M10 (V040, 2026-05-06): 同 supplier × txMonth の税率別行群のうち、
     * 1 行でも MANUAL_VERIFICATION (UI 手入力 verify) があるかを判定する。
     * <p>true の場合、再 upload (applyVerification) は当該 supplier 全体を保護対象としてスキップする。
     * <p>判定は {@code verification_source} 列で行い、verification_note 接頭辞には依存しない
     * (旧実装は note 文字列推定で偽判定リスクがあった)。
     */
    static boolean isAnyManuallyLocked(List<TAccountsPayableSummary> group) {
        return group.stream().anyMatch(s ->
                Boolean.TRUE.equals(s.getVerifiedManually())
                        && FinanceConstants.VERIFICATION_SOURCE_MANUAL.equals(s.getVerificationSource()));
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
     * <p>SF-C21: 同一 (shop_no, transactionMonth) に対する {@link #applyVerification} と本メソッドは
     * advisory lock {@code pg_advisory_xact_lock} で直列化される。advisory lock は
     * {@link Transactional @Transactional} 境界で自動解放されるため、対象 0 件等で early return
     * しても解放漏れはない (PostgreSQL 仕様)。
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
            throw new IllegalArgumentException(
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

            // SF-C03: CSV「取引日」列は 小田光の締め日 = transactionMonth (前月20日) 固定。
            // mf_transfer_date (Excel 振込明細の送金日) は監査用 DB 列としてのみ保持し、
            // CSV には載せない (送金日は MF 銀行データ連携で自動付与されるため)。
            String sourceName = rule.getSourceName();
            PaymentMfPreviewRow row = PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(s.getSupplierCode())
                    .sourceName(sourceName)
                    .amount(amount)
                    .transactionDate(transactionMonth)
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
        // SF-C03: 取引日は CSV 列としては transactionMonth (締め日) 固定。aux.transferDate (送金日) は
        // DB 監査列として保持しソート用途にのみ使用する。
        for (TPaymentMfAuxRow aux : auxRows) {
            long amount = aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount());
            totalAmount += amount;
            csvRows.add(PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(aux.getPaymentSupplierCode())
                    .sourceName(aux.getSourceName())
                    .amount(amount)
                    .transactionDate(transactionMonth)
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
            throw new IllegalArgumentException("CSV 出力可能な行がありません（ルール未登録のみ・補助行なし）: " + skipped);
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
    public void saveVerifiedExportHistory(LocalDate transactionMonth,
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

        // N+1 解消 (B-W11): エントリ全走査より先に reconcileCode を集め、対象月の payable を一括ロード。
        // 以前は reconcile() 呼び出しの度に findByShopNoAndSupplierCodeAndTransactionMonth が走っていた。
        Map<String, List<TAccountsPayableSummary>> payablesByCode = java.util.Collections.emptyMap();
        if (txMonth != null) {
            Set<String> reconcileCodes = new java.util.LinkedHashSet<>();
            for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
                MPaymentMfRule rule = null;
                if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
                if (rule == null) rule = bySource.get(normalize(e.sourceName));
                if (rule == null) continue;
                // 20日払いセクションの PAYABLE は DIRECT_PURCHASE 扱いで突合対象外。
                if (e.section == PaymentMfSection.PAYMENT_20TH
                        && "PAYABLE".equals(rule.getRuleKind())) continue;
                if (!"PAYABLE".equals(rule.getRuleKind())) continue;
                String code = e.supplierCode != null ? e.supplierCode
                        : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                                ? rule.getPaymentSupplierCode() : null);
                if (code != null) reconcileCodes.add(code);
            }
            if (!reconcileCodes.isEmpty()) {
                payablesByCode = payableRepository
                        .findByShopNoAndSupplierCodeInAndTransactionMonth(
                                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, reconcileCodes, txMonth)
                        .stream()
                        .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));
            }
        }

        List<PaymentMfPreviewRow> rows = new ArrayList<>();
        Set<String> unregistered = new java.util.LinkedHashSet<>();
        // PAYABLE ルールがマッチしたが payment_supplier_code が未設定の送り先。
        // 検証済みCSV出力で CSV 除外されるため、一括検証前に補完を促す。
        Set<String> rulesMissingCode = new java.util.LinkedHashSet<>();
        int matched = 0, diff = 0, unmatched = 0, errors = 0;
        long totalAmount = 0L;
        long preTotalAmount = 0L;   // 合計行前（PAYABLE+EXPENSE）の請求額合計
        long directPurchaseTotal = 0L; // 合計行後（DIRECT_PURCHASE）の請求額合計
        // P1-03 案 D: per-supplier の振込/控除合計を蓄積し、Excel 合計行と突合する。
        // G2-M3: section 別 (5日払い / 20日払い) に集計し、各 section の合計行 summary と個別に突合する。
        Map<PaymentMfSection, long[]> perSection = new java.util.EnumMap<>(PaymentMfSection.class);
        for (PaymentMfSection s : PaymentMfSection.values()) {
            // [transfer, fee, discount, early, offset, invoice] の 6 要素
            perSection.put(s, new long[6]);
        }
        // per-supplier 1 円不一致 (請求 != 振込 + 控除合計) の行を集約。Excel 入力ミス検知用。
        List<String> perSupplierMismatch = new ArrayList<>();

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));

            // 20日払いセクション は買掛金(PAYABLE)ではなく仕入高(DIRECT_PURCHASE)扱い (G2-M3: section enum で判定)。
            // SF-C07: 元ルールの summaryTemplate / tag / creditDepartment を継承する
            // (継承漏れによる摘要・タグ・部門欠落の防止)。
            if (rule != null && e.section == PaymentMfSection.PAYMENT_20TH
                    && "PAYABLE".equals(rule.getRuleKind())) {
                rule = MPaymentMfRule.builder()
                        .sourceName(rule.getSourceName())
                        .ruleKind("DIRECT_PURCHASE")
                        .debitAccount("仕入高")
                        .debitSubAccount(null)
                        .debitDepartment(null)
                        .debitTaxCategory("課税仕入 10%")
                        .creditAccount("資金複合")
                        .creditDepartment(rule.getCreditDepartment())
                        .creditTaxCategory("対象外")
                        .summaryTemplate(rule.getSummaryTemplate() != null
                                ? rule.getSummaryTemplate() : "{source_name}")
                        .tag(rule.getTag())
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
            if (e.section == PaymentMfSection.PAYMENT_20TH) {
                directPurchaseTotal += e.amount;
            } else {
                preTotalAmount += e.amount;
            }
            // P1-03 案 D-2 / P1-07 案 D: 5日払い PAYABLE および 20日払い DIRECT_PURCHASE
            // (自動降格 + 元 DIRECT_PURCHASE 両方含む) で supplier 別 attribute
            // (送料相手/値引/早払/相殺) を反映する。
            //
            // 主行の貸方金額は「振込金額」(= 請求 - 送料相手 - 値引 - 早払 - 相殺) に切替えて
            // 銀行通帳の振込金額と一致させる。
            //   - PAYABLE 主行: 借方=買掛金 / 貸方=普通預金 (= 振込)
            //   - DIRECT_PURCHASE 主行: 借方=仕入高 / 貸方=資金複合 (= 振込)
            // 副行 (PAYABLE_*/DIRECT_PURCHASE_* FEE/DISCOUNT/EARLY/OFFSET) は
            //   - PAYABLE 系: 借方=買掛金、貸方=該当勘定で残額を消込
            //   - DIRECT_PURCHASE 系: 借方=仕入高、貸方=該当勘定 (買掛金経由しない即払いのため)
            // amount>0 のときのみ生成。
            boolean needsSubRows = "PAYABLE".equals(rule.getRuleKind())
                    || "DIRECT_PURCHASE".equals(rule.getRuleKind());
            boolean isDirectPurchase = "DIRECT_PURCHASE".equals(rule.getRuleKind());
            String section = e.section == PaymentMfSection.PAYMENT_20TH ? "20日払い" : "5日払い";
            long mainCreditAmount = e.amount;
            long feeAmt = 0L, discountAmt = 0L, earlyAmt = 0L, offsetAmt = 0L;
            if (needsSubRows) {
                feeAmt = e.fee == null ? 0L : Math.max(0L, e.fee);
                discountAmt = e.discount == null ? 0L : Math.max(0L, e.discount);
                earlyAmt = e.earlyPayment == null ? 0L : Math.max(0L, e.earlyPayment);
                offsetAmt = e.offset == null ? 0L : Math.max(0L, e.offset);
                long deduction = feeAmt + discountAmt + earlyAmt + offsetAmt;
                // Excel 列 E (振込金額) を優先。NULL なら派生計算 (請求 - 控除合計)。
                if (e.transferAmount != null) {
                    mainCreditAmount = e.transferAmount;
                } else {
                    mainCreditAmount = e.amount - deduction;
                }
                // per-supplier 整合性チェック (1 円不許容): 請求額 = 振込 + 送料相手 + 値引 + 早払 + 相殺
                long expected = mainCreditAmount + deduction;
                if (e.transferAmount != null && expected != e.amount) {
                    String msg = "[" + section + "] " + e.sourceName
                            + "(row=" + e.rowIndex + ", code=" + e.supplierCode
                            + "): 請求=" + e.amount + " != 振込" + mainCreditAmount
                            + " + 送料" + feeAmt + " + 値引" + discountAmt
                            + " + 早払" + earlyAmt + " + 相殺" + offsetAmt
                            + " (差=" + (e.amount - expected) + ")";
                    log.warn("per-supplier 整合性不一致 {}", msg);
                    perSupplierMismatch.add(msg);
                }
                // per-supplier 集計 (合計行突合用) — section 別に分けて集計し、
                // 各 section の合計行 summary と独立に chk3 を判定する (G2-M3)。
                long[] acc = perSection.get(e.section);
                acc[0] += mainCreditAmount; // transfer
                acc[1] += feeAmt;           // fee
                acc[2] += discountAmt;      // discount
                acc[3] += earlyAmt;         // early
                acc[4] += offsetAmt;        // offset
                acc[5] += e.amount;         // invoice (請求額)
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
                    .creditAmount(needsSubRows ? mainCreditAmount : e.amount)
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
                // 事前取得済みの payablesByCode から参照 (B-W11 N+1 解消)。
                ReconcileResult rr = reconcileFromPayables(
                        payablesByCode.get(reconcileCode), e.amount);
                b.matchStatus(rr.status).payableAmount(rr.payableAmount)
                        .payableDiff(rr.diff).supplierNo(rr.supplierNo);
                if ("MATCHED".equals(rr.status)) matched++;
                else if ("DIFF".equals(rr.status)) diff++;
                else unmatched++;
            } else {
                b.matchStatus("NA");
            }
            rows.add(b.build());

            // P1-03 案 D-2 / P1-07 案 D: 5日払い PAYABLE / 20日払い DIRECT_PURCHASE 副行を amount>0 のとき追加。
            // PAYABLE 系: 借方=買掛金 (親 PAYABLE と同じ)、貸方=該当勘定
            // DIRECT_PURCHASE 系: 借方=仕入高 (親 DIRECT_PURCHASE と同じ)、貸方=該当勘定
            // supplier_code/sourceName は親と同じ。
            if (needsSubRows) {
                String feeKind = isDirectPurchase ? "DIRECT_PURCHASE_FEE" : "PAYABLE_FEE";
                String discountKind = isDirectPurchase ? "DIRECT_PURCHASE_DISCOUNT" : "PAYABLE_DISCOUNT";
                String earlyKind = isDirectPurchase ? "DIRECT_PURCHASE_EARLY" : "PAYABLE_EARLY";
                String offsetKind = isDirectPurchase ? "DIRECT_PURCHASE_OFFSET" : "PAYABLE_OFFSET";
                if (feeAmt > 0L) {
                    rows.add(buildAttributeSubRow(rule, e, feeKind, feeAmt,
                            "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                            "振込手数料値引／" + e.sourceName));
                }
                if (discountAmt > 0L) {
                    rows.add(buildAttributeSubRow(rule, e, discountKind, discountAmt,
                            "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                            "値引／" + e.sourceName));
                }
                if (earlyAmt > 0L) {
                    rows.add(buildAttributeSubRow(rule, e, earlyKind, earlyAmt,
                            "早払収益", "物販事業部", "非課税売上",
                            "早払収益／" + e.sourceName));
                }
                if (offsetAmt > 0L) {
                    // G2-M8: OFFSET 副行貸方科目はマスタ管理に移行 (m_offset_journal_rule)。
                    // 税理士確認後に admin が UI から値を変更可能。
                    // V041 seed では従来ハードコード値 (仕入値引・戻し高 / 物販事業部 /
                    // 課税仕入-返還等 10%) と同一の値を投入しているため migration 適用直後は挙動不変。
                    //
                    // Codex Major fix: マスタ欠落 (将来 shop_no=2 追加 / admin UI で誤って論理削除)
                    // による preview/apply ブロックを防ぐため、orElseThrow ではなく hardcoded default で
                    // フォールバック (= V041 seed と同値) し、WARN ログで運用者に通知する。
                    // 二段防御として MOffsetJournalRuleService.delete() で「最後の active 行」削除を禁止する。
                    MOffsetJournalRule offsetRule = offsetJournalRuleRepository
                            .findByShopNoAndDelFlg(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, "0")
                            .orElseGet(() -> {
                                log.warn("m_offset_journal_rule の shop_no={} (active) 行が未登録のため"
                                        + " hardcoded default (V041 seed と同値) で fallback します。"
                                        + " admin UI から正規行を再投入してください。",
                                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);
                                return MOffsetJournalRule.builder()
                                        .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                                        .creditAccount("仕入値引・戻し高")
                                        .creditDepartment("物販事業部")
                                        .creditTaxCategory("課税仕入-返還等 10%")
                                        .summaryPrefix("相殺／")
                                        .delFlg("0")
                                        .build();
                            });
                    rows.add(buildAttributeSubRow(rule, e, offsetKind, offsetAmt,
                            offsetRule.getCreditAccount(),
                            offsetRule.getCreditDepartment(),
                            offsetRule.getCreditTaxCategory(),
                            offsetRule.getSummaryPrefix() + e.sourceName));
                }
            }
        }

        // P1-03 案 D: 旧 SUMMARY 集約 (振込手数料値引/早払収益 合計仕訳 2 行) を撤去。
        // 代わりに supplier 別に展開された PAYABLE_FEE / PAYABLE_EARLY 行で同等の会計表現になる。

        // 整合性チェック (1 円も許容しない)
        // G2-M3: 旧実装は最初の合計行 (5日払い) のみ summary を捕まえて以後の合計行 (20日払い) を捨てていた。
        // 現在は section 別 summary を持つので、section ごとに chk1/chk3 を判定し、
        // mismatch を per-supplier 違反一覧に [5日払い] / [20日払い] 接頭辞付きで追記する。
        // UI/DTO 互換性のため、AmountReconciliation の summary* 系フィールドは
        // 両 section 合算値 (= 旧 v1 ParsedExcel 構造と同一意味) を返す。
        PaymentMfExcelParser.SectionSummary sum5 = summaryOf(cached, PaymentMfSection.PAYMENT_5TH);
        PaymentMfExcelParser.SectionSummary sum20 = summaryOf(cached, PaymentMfSection.PAYMENT_20TH);

        long summary5Fee = summaryLong(sum5, s -> s.sourceFee);
        long summary5Early = summaryLong(sum5, s -> s.earlyPayment);
        long summary5Transfer = summaryLong(sum5, s -> s.transferAmount);
        long summary5Invoice = summaryLong(sum5, s -> s.invoiceTotal);
        long summary20Fee = summaryLong(sum20, s -> s.sourceFee);
        long summary20Early = summaryLong(sum20, s -> s.earlyPayment);
        long summary20Transfer = summaryLong(sum20, s -> s.transferAmount);
        long summary20Invoice = summaryLong(sum20, s -> s.invoiceTotal);

        long summaryFee = summary5Fee + summary20Fee;
        long summaryEarly = summary5Early + summary20Early;
        long summaryTransfer = summary5Transfer + summary20Transfer;
        long summaryInvoice = summary5Invoice + summary20Invoice;

        // チェック1 (G2-M3: section 別判定): C(請求額) - F(振込手数料) - H(早払) == E(振込金額)
        // section が空 (= 5日払いのみの Excel で 20日払い summary なし) の場合は 0 + 0 で自動 match。
        long expected5Transfer = summary5Invoice - summary5Fee - summary5Early;
        long expected20Transfer = summary20Invoice - summary20Fee - summary20Early;
        long excel5Diff = sum5 == null ? 0L : (summary5Transfer - expected5Transfer);
        long excel20Diff = sum20 == null ? 0L : (summary20Transfer - expected20Transfer);
        // UI 互換: 旧フィールド (excelDifference/expectedTransferAmount) は両 section 合算値で返す。
        long expectedTransfer = expected5Transfer + expected20Transfer;
        long excelDifference = excel5Diff + excel20Diff;
        // section 別判定: 片方でも非ゼロなら NG (合算で偶然打ち消し合う旧 BUG を防ぐ)。
        boolean excelMatched = excel5Diff == 0 && excel20Diff == 0;
        if (sum5 != null && excel5Diff != 0) {
            log.warn("[5日払い] 合計行 列間整合違反: C(請求){} - F(送料相手){} - H(早払){} = {} != E(振込){} (差={})",
                    summary5Invoice, summary5Fee, summary5Early, expected5Transfer, summary5Transfer, excel5Diff);
        }
        if (sum20 != null && excel20Diff != 0) {
            log.warn("[20日払い] 合計行 列間整合違反: C(請求){} - F(送料相手){} - H(早払){} = {} != E(振込){} (差={})",
                    summary20Invoice, summary20Fee, summary20Early, expected20Transfer, summary20Transfer, excel20Diff);
        }

        // チェック2: 明細行の読取り整合 — sum(5日払い 明細 請求額) == 5日払い 合計行 C
        // (20日払い側は DIRECT_PURCHASE 扱いで preTotalAmount に含まれないため、
        //  preTotalAmount は 5日払い summary との突合のみで意味を持つ)。
        long readDifference = preTotalAmount - summary5Invoice;
        boolean readMatched = readDifference == 0;

        // P1-03 案 D チェック3 (G2-M3 で section 別化):
        // 5日払い per-supplier 振込金額合計 == 5日払い 合計行 E
        // 20日払い per-supplier 振込金額合計 == 20日払い 合計行 E
        // ※ EXPENSE rule の行は needsSubRows=false で per-supplier accumulator に入らないため、
        //   EXPENSE 主体の section では合算が summary より少なくなる (= 旧来からの既知の限界)。
        //   従来同様、ここでは boolean フラグだけ立てて perSupplierMismatches へは追加しない。
        long[] s5 = perSection.get(PaymentMfSection.PAYMENT_5TH);
        long[] s20 = perSection.get(PaymentMfSection.PAYMENT_20TH);
        long perSupplier5TransferDiff = sum5 == null ? 0L : (s5[0] - summary5Transfer);
        long perSupplier20TransferDiff = sum20 == null ? 0L : (s20[0] - summary20Transfer);
        long sumPerSupplierTransfer = s5[0] + s20[0];
        long sumPerSupplierFee = s5[1] + s20[1];
        long sumPerSupplierDiscount = s5[2] + s20[2];
        long sumPerSupplierEarly = s5[3] + s20[3];
        long sumPerSupplierOffset = s5[4] + s20[4];
        long perSupplierTransferDiff = perSupplier5TransferDiff + perSupplier20TransferDiff;
        boolean perSupplierTransferMatched = perSupplier5TransferDiff == 0 && perSupplier20TransferDiff == 0;
        if (sum5 != null && perSupplier5TransferDiff != 0) {
            log.warn("[5日払い] 全体振込整合違反: Σ supplier 振込{} != E(合計振込){} (差={})",
                    s5[0], summary5Transfer, perSupplier5TransferDiff);
        }
        if (sum20 != null && perSupplier20TransferDiff != 0) {
            log.warn("[20日払い] 全体振込整合違反: Σ supplier 振込{} != E(合計振込){} (差={})",
                    s20[0], summary20Transfer, perSupplier20TransferDiff);
        }

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
                        .perSupplierTransferSum(sumPerSupplierTransfer)
                        .perSupplierTransferDiff(perSupplierTransferDiff)
                        .perSupplierTransferMatched(perSupplierTransferMatched)
                        .perSupplierFeeSum(sumPerSupplierFee)
                        .perSupplierDiscountSum(sumPerSupplierDiscount)
                        .perSupplierEarlySum(sumPerSupplierEarly)
                        .perSupplierOffsetSum(sumPerSupplierOffset)
                        .perSupplierMismatches(perSupplierMismatch)
                        .build();

        // P1-08 Phase 1: L1 (重複ハッシュ) + L2 (確定済) 警告生成
        DuplicateWarning duplicateWarning = buildDuplicateWarning(cached.getSourceFileHash());
        AppliedWarning appliedWarning = buildAppliedWarning(cached.getTransferDate());

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
                .duplicateWarning(duplicateWarning)
                .appliedWarning(appliedWarning)
                .build();
    }

    /**
     * P1-08 L1: 同一 SHA-256 ハッシュの過去取込があれば最新を返す。
     * fileHash が null (旧 cache 残存) の場合は null。
     */
    private DuplicateWarning buildDuplicateWarning(String fileHash) {
        if (fileHash == null || fileHash.isBlank()) return null;
        return historyRepository
                .findBySourceFileHashAndDelFlgOrderByAddDateTimeDesc(fileHash, "0")
                .stream()
                .findFirst()
                .map(h -> new DuplicateWarning(
                        h.getAddDateTime() == null ? null
                                : h.getAddDateTime().atOffset(ZoneOffset.ofHours(9)),
                        h.getSourceFilename(),
                        h.getAddUserNo()))
                .orElse(null);
    }

    /**
     * P1-08 L2: 同 (shop=1, transferDate) で applyVerification 済 (applied_at != NULL) の最新行があれば返す。
     * transferDate が null (Excel から取れない異常ケース) の場合は警告対象外。
     */
    private AppliedWarning buildAppliedWarning(LocalDate transferDate) {
        if (transferDate == null) return null;
        return historyRepository
                .findFirstByShopNoAndTransferDateAndAppliedAtNotNullAndDelFlgOrderByAppliedAtDesc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transferDate, "0")
                .map(h -> {
                    OffsetDateTime appliedAt = h.getAppliedAt() == null ? null
                            : h.getAppliedAt().atOffset(ZoneOffset.ofHours(9));
                    LocalDate txMonth = deriveTransactionMonth(transferDate);
                    return new AppliedWarning(appliedAt, h.getAppliedByUserNo(), txMonth, transferDate);
                })
                .orElse(null);
    }

    /**
     * P1-03 案 D-2 / P1-07 案 D: PAYABLE / DIRECT_PURCHASE 副行 (FEE/DISCOUNT/EARLY/OFFSET) を生成する。
     * <p>借方は親 (買掛金 or 仕入高) を継承、貸方は属性別の勘定。
     * <ul>
     *   <li>PAYABLE 系 (5日払い): 借方=買掛金/{MF補助科目}/対象外、貸方=該当勘定</li>
     *   <li>DIRECT_PURCHASE 系 (20日払い・自動降格含む): 借方=仕入高/課税仕入 10%、貸方=該当勘定</li>
     * </ul>
     * supplier_code / source_name は親と同じ (突合履歴の supplier 紐付け維持のため)。
     * matchStatus は "NA" 固定 (副行は買掛金一覧との突合対象外。請求額消込は親で管理)。
     */
    private PaymentMfPreviewRow buildAttributeSubRow(MPaymentMfRule rule,
                                                     PaymentMfExcelParser.ParsedEntry e,
                                                     String subRuleKind,
                                                     long amount,
                                                     String creditAccount,
                                                     String creditDepartment,
                                                     String creditTax,
                                                     String summary) {
        return PaymentMfPreviewRow.builder()
                .excelRowIndex(e.rowIndex)
                .paymentSupplierCode(e.supplierCode)
                .sourceName(e.sourceName)
                .amount(amount)
                .ruleKind(subRuleKind)
                // 借方 親ルール継承 (PAYABLE→買掛金 / DIRECT_PURCHASE→仕入高)
                .debitAccount(rule.getDebitAccount())
                .debitSubAccount(rule.getDebitSubAccount())
                .debitDepartment(rule.getDebitDepartment())
                .debitTax(rule.getDebitTaxCategory())
                .debitAmount(amount)
                // 貸方 属性別勘定
                .creditAccount(creditAccount)
                .creditDepartment(creditDepartment)
                .creditTax(creditTax)
                .creditAmount(amount)
                .summary(summary)
                .tag(rule.getTag())
                .matchStatus("NA")
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
     * <p>現行仕様: 5日/20日とも前月20日締め (設計書 §5.1 と一致)。
     * <ul>
     *   <li>5日送金  = 前月20日締め残高の主精算</li>
     *   <li>20日送金 = 同じ前月20日締め残高の2回目精算（早払い／残金）。
     *       20日は「当月の締め日」でもあるが、その日の送金は当日時点で既に確定している
     *       前月締め残高に対して行うのが運用。当月分(2026-04-20締め)は翌月5日送金で支払う。</li>
     * </ul>
     * <p>例) 2026/02/05 → 2026-01-20
     *       2026/02/20 → 2026-01-20
     *       2026/04/20 → 2026-03-20
     */
    static LocalDate deriveTransactionMonth(LocalDate transferDate) {
        return transferDate.minusMonths(1).withDayOfMonth(20);
    }

    private static class ReconcileResult {
        String status; Long payableAmount; Long diff; Integer supplierNo;
    }

    /**
     * 事前取得した payable リストから突合判定する (N+1 解消版)。
     * list が null/空なら UNMATCHED。
     */
    private ReconcileResult reconcileFromPayables(List<TAccountsPayableSummary> list, Long invoiceAmount) {
        ReconcileResult r = new ReconcileResult();
        if (list == null || list.isEmpty()) {
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
                    // P1-08: convert 経由でも sourceFileHash を残す → 後続 preview の L1 警告で同一 Excel 検知。
                    .sourceFileHash(cached.getSourceFileHash())
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
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("PaymentMf cache: {}件期限切れ削除 (残{}件)", removed, cache.size());
        }
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

    /**
     * P1-08: 取込元 Excel バイト列の SHA-256 を hex 文字列で返す。
     * 同一バイト列なら同一 hash → preview L1 警告で重複取込検知に使う。
     */
    static String computeSha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ===========================================================
    // DTOs (internal)
    // ===========================================================

    @Data
    @Builder
    static class CachedUpload {
        private List<PaymentMfExcelParser.ParsedEntry> entries;
        private LocalDate transferDate;
        /**
         * G2-M3 (2026-05-06): 旧 {@code summarySourceFee/EarlyPayment/TransferAmount/InvoiceTotal}
         * のフラットなフィールドを廃止し、section 別 summary の Map に置き換え。
         * 5日払いのみの Excel では PAYMENT_5TH のみ入り、PAYMENT_20TH は欠落 (空セクション)。
         */
        private Map<PaymentMfSection, PaymentMfExcelParser.SectionSummary> summaries;
        private String fileName;
        /** P1-08: 取込元 Excel の SHA-256 (hex)。convert / applyVerification で history 行に永続化。 */
        private String sourceFileHash;
        private long expiresAt;
    }

    /**
     * G2-M3: cached.summaries から section の {@link PaymentMfExcelParser.SectionSummary} を取得する。
     * 該当 section が無い (= 5日払いのみの Excel で 20日払いを問い合わせ等) 場合は null を返す。
     * 値段の {@code Long} 取得は {@link #summaryLong} を使うこと。
     */
    private static PaymentMfExcelParser.SectionSummary summaryOf(
            CachedUpload cached, PaymentMfSection section) {
        if (cached == null || cached.getSummaries() == null) return null;
        return cached.getSummaries().get(section);
    }

    /** G2-M3: SectionSummary から Long を取り出して null は 0 にする (整合性チェック用)。 */
    private static long summaryLong(PaymentMfExcelParser.SectionSummary s,
                                    java.util.function.Function<PaymentMfExcelParser.SectionSummary, Long> getter) {
        if (s == null) return 0L;
        Long v = getter.apply(s);
        return v == null ? 0L : v;
    }
}
