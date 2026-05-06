package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.AccountingStatusService;
import jp.co.oda32.domain.service.finance.AccountsPayableIntegrityService;
import jp.co.oda32.domain.service.finance.AccountsPayableLedgerService;
import jp.co.oda32.domain.service.finance.ConsistencyReviewService;
import jp.co.oda32.domain.service.finance.InvoiceImportService;
import jp.co.oda32.domain.service.finance.MPartnerGroupService;
import jp.co.oda32.domain.service.finance.MfHealthCheckService;
import jp.co.oda32.domain.service.finance.PurchaseJournalCsvService;
import jp.co.oda32.domain.service.finance.SupplierBalancesService;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfSupplierLedgerService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse;
import jp.co.oda32.dto.finance.AccountsPayableResponse;
import jp.co.oda32.dto.finance.AccountsPayableSummaryResponse;
import jp.co.oda32.dto.finance.IntegrityReportResponse;
import jp.co.oda32.dto.finance.MfHealthResponse;
import jp.co.oda32.dto.finance.MfSupplierLedgerResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.AccountsPayableVerifyRequest;
import jp.co.oda32.dto.finance.BulkPaymentDateRequest;
import jp.co.oda32.dto.finance.ConsistencyReviewRequest;
import jp.co.oda32.dto.finance.ConsistencyReviewResponse;
import jp.co.oda32.dto.finance.MfExportToggleRequest;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import jp.co.oda32.dto.finance.InvoiceResponse;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import jp.co.oda32.dto.finance.PartnerGroupResponse;
import jp.co.oda32.dto.finance.PaymentDateUpdateRequest;
import jp.co.oda32.dto.finance.PurchaseJournalExportPreviewResponse;
import jp.co.oda32.exception.FinanceBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Validated
public class FinanceController {

    private static final Charset CP932 = Charset.forName("windows-31j");

    private final TAccountsPayableSummaryService accountsPayableSummaryService;
    private final TInvoiceService tInvoiceService;
    private final InvoiceImportService invoiceImportService;
    private final MPartnerGroupService partnerGroupService;
    private final AccountingStatusService accountingStatusService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final PurchaseJournalCsvService purchaseJournalCsvService;
    private final AccountsPayableLedgerService accountsPayableLedgerService;
    private final MfSupplierLedgerService mfSupplierLedgerService;
    private final AccountsPayableIntegrityService accountsPayableIntegrityService;
    private final SupplierBalancesService supplierBalancesService;
    private final MfHealthCheckService mfHealthCheckService;
    private final MfJournalCacheService mfJournalCacheService;
    private final ConsistencyReviewService consistencyReviewService;

    @GetMapping("/accounts-payable")
    public ResponseEntity<Page<AccountsPayableResponse>> listAccountsPayable(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(required = false) String verificationFilter,
            @RequestParam(required = false) String include,
            @PageableDefault(size = 50, sort = "supplierCode", direction = Sort.Direction.ASC) Pageable pageable) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        boolean includeBalance = include != null
                && java.util.Arrays.stream(include.split(","))
                        .map(String::trim)
                        .anyMatch("balance"::equalsIgnoreCase);
        Page<TAccountsPayableSummary> page = accountsPayableSummaryService.findPaged(
                effectiveShopNo, supplierNo, transactionMonth, verificationFilter, pageable);

        Set<Integer> supplierNos = page.getContent().stream()
                .map(TAccountsPayableSummary::getSupplierNo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, MPaymentSupplier> psMap = mPaymentSupplierService.findAllByPaymentSupplierNos(supplierNos).stream()
                .collect(Collectors.toMap(MPaymentSupplier::getPaymentSupplierNo, p -> p, (a, b) -> a));

        return ResponseEntity.ok(page.map(ap -> AccountsPayableResponse.from(
                ap, psMap.get(ap.getSupplierNo()), includeBalance)));
    }

    @GetMapping("/accounts-payable/summary")
    public ResponseEntity<AccountsPayableSummaryResponse> getAccountsPayableSummary(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        return ResponseEntity.ok(accountsPayableSummaryService.summary(effectiveShopNo, transactionMonth));
    }

    @PutMapping("/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/verify")
    public ResponseEntity<AccountsPayableResponse> verifyAccountsPayable(
            @PathVariable Integer shopNo,
            @PathVariable Integer supplierNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @Valid @RequestBody AccountsPayableVerifyRequest request) {
        assertShopAccess(shopNo);
        TAccountsPayableSummary updated = accountsPayableSummaryService.verify(
                shopNo, supplierNo, transactionMonth, taxRate,
                request.getVerifiedAmount(), request.getNote());
        MPaymentSupplier ps = mPaymentSupplierService.getByPaymentSupplierNo(supplierNo);
        return ResponseEntity.ok(AccountsPayableResponse.from(updated, ps));
    }

    @DeleteMapping("/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/manual-lock")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<AccountsPayableResponse> releaseManualLock(
            @PathVariable Integer shopNo,
            @PathVariable Integer supplierNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate) {
        assertShopAccess(shopNo);
        TAccountsPayableSummary updated = accountsPayableSummaryService.releaseManualLock(
                shopNo, supplierNo, transactionMonth, taxRate);
        MPaymentSupplier ps = mPaymentSupplierService.getByPaymentSupplierNo(supplierNo);
        return ResponseEntity.ok(AccountsPayableResponse.from(updated, ps));
    }

    @PatchMapping("/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/mf-export")
    public ResponseEntity<AccountsPayableResponse> toggleMfExport(
            @PathVariable Integer shopNo,
            @PathVariable Integer supplierNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @Valid @RequestBody MfExportToggleRequest request) {
        assertShopAccess(shopNo);
        TAccountsPayableSummary updated = accountsPayableSummaryService.updateMfExport(
                shopNo, supplierNo, transactionMonth, taxRate, request.getEnabled());
        MPaymentSupplier ps = mPaymentSupplierService.getByPaymentSupplierNo(supplierNo);
        return ResponseEntity.ok(AccountsPayableResponse.from(updated, ps));
    }

    private void assertShopAccess(Integer shopNo) {
        Integer effective = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        if (effective != null && !effective.equals(shopNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
        }
    }

    // -------- 買掛帳 (1 仕入先の月次推移) --------

    /**
     * 1 仕入先について指定期間 (最大 24 ヶ月) の月次 ledger を返す。
     * 設計書: claudedocs/design-accounts-payable-ledger.md §4.1
     *
     * @since 2026-04-22 (買掛帳画面)
     */
    @GetMapping("/accounts-payable/ledger")
    public ResponseEntity<AccountsPayableLedgerResponse> getAccountsPayableLedger(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("supplierNo") Integer supplierNo,
            @RequestParam("fromMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam("toMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth) {
        assertShopAccess(shopNo);
        AccountsPayableLedgerResponse res = accountsPayableLedgerService.getLedger(shopNo, supplierNo, fromMonth, toMonth);
        return ResponseEntity.ok(res);
    }

    /**
     * 1 仕入先について MF /journals を期間累積で取得し、月次 credit/debit delta を返す。
     * 買掛帳の「MF と比較」ボタンから明示的に呼ばれる想定。
     * 設計書: claudedocs/design-accounts-payable-ledger.md §4.2
     *
     * @since 2026-04-22 (買掛帳画面)
     */
    /**
     * 買掛帳 整合性検出 (軸 B + 軸 C): 全 supplier 一括診断。
     * 設計書: claudedocs/design-integrity-report.md
     *
     * @since 2026-04-22
     */
    @GetMapping("/accounts-payable/integrity-report")
    public ResponseEntity<IntegrityReportResponse> getIntegrityReport(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("fromMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam("toMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        assertShopAccess(shopNo);
        // MF 認証/権限/計算失敗系の例外は FinanceExceptionHandler に委譲 (SF-25)
        IntegrityReportResponse res = accountsPayableIntegrityService.generate(shopNo, fromMonth, toMonth, refresh);
        return ResponseEntity.ok(res);
    }

    /**
     * 軸 D: supplier 累積残一覧。
     * 期首 (2025-05-20) 〜 asOfMonth の全 supplier 累積残を自社 / MF で突合し、
     * MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING で分類。
     *
     * @param shopNo     ショップ番号 (必須)
     * @param asOfMonth  基準月 (20日締め、省略時は最新月)
     * @param refresh    true で対象期間の MF journals キャッシュを discard → 再取得
     * @since 2026-04-23
     */
    @GetMapping("/accounts-payable/supplier-balances")
    public ResponseEntity<SupplierBalancesResponse> getSupplierBalances(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam(value = "asOfMonth", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfMonth,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        assertShopAccess(shopNo);
        // MF 認証/権限/計算失敗系の例外は FinanceExceptionHandler に委譲 (SF-25)
        SupplierBalancesResponse res = supplierBalancesService.generate(shopNo, asOfMonth, refresh);
        return ResponseEntity.ok(res);
    }

    /**
     * 差分確認 (案 X+Y): 整合性レポートの差分行に IGNORE / MF_APPLY を記録。
     * @since 2026-04-23
     */
    @PostMapping("/accounts-payable/integrity-report/reviews")
    public ResponseEntity<ConsistencyReviewResponse> saveConsistencyReview(
            @RequestBody @Valid ConsistencyReviewRequest req) throws Exception {
        assertShopAccess(req.getShopNo());
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        ConsistencyReviewResponse res = consistencyReviewService.upsert(req, userNo);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @DeleteMapping("/accounts-payable/integrity-report/reviews")
    public ResponseEntity<Void> deleteConsistencyReview(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("entryType") String entryType,
            @RequestParam("entryKey") String entryKey,
            @RequestParam("transactionMonth")
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth) {
        assertShopAccess(shopNo);
        consistencyReviewService.delete(shopNo, entryType, entryKey, transactionMonth);
        return ResponseEntity.noContent().build();
    }

    /**
     * 軸 E: MF 連携ヘルスチェック。
     * MF OAuth 状態 / 買掛金サマリ集計 / anomaly / journals cache を 1 レスポンスで返す。
     */
    @GetMapping("/mf-health")
    public ResponseEntity<MfHealthResponse> getMfHealth(@RequestParam("shopNo") Integer shopNo) {
        assertShopAccess(shopNo);
        return ResponseEntity.ok(mfHealthCheckService.check(shopNo));
    }

    /**
     * 軸 E: MF journals キャッシュを shop 単位で全破棄。
     */
    @PostMapping("/mf-health/cache/invalidate")
    public ResponseEntity<Void> invalidateMfCache(@RequestParam("shopNo") Integer shopNo) {
        assertShopAccess(shopNo);
        mfJournalCacheService.invalidateAll(shopNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts-payable/ledger/mf")
    public ResponseEntity<MfSupplierLedgerResponse> getMfSupplierLedger(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("supplierNo") Integer supplierNo,
            @RequestParam("fromMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam("toMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        assertShopAccess(shopNo);
        // MF 認証/権限/計算失敗系の例外は FinanceExceptionHandler に委譲 (SF-25)
        MfSupplierLedgerResponse res = mfSupplierLedgerService.getSupplierLedger(shopNo, supplierNo, fromMonth, toMonth, refresh);
        return ResponseEntity.ok(res);
    }

    // -------- 買掛→仕入仕訳 CSV（MF）--------

    /**
     * 指定取引月の買掛金サマリから、買掛→仕入仕訳 MF CSV をブラウザに直接ダウンロードさせる。
     * バッチ {@code purchaseJournalIntegration} と同じロジックだが、
     * サーバー上のファイル書き出しではなくレスポンスとして返す。
     * <p>出力行は supplier × taxRate で集約され、借方「仕入高」/ 貸方「買掛金」の仕訳になる。
     *
     * @param transactionMonth 対象取引月 (yyyy-MM-dd)
     * @param forceExport      true の場合 MF出力OFF の行も含めて出力（未検証含む）
     */
    @GetMapping("/accounts-payable/export-purchase-journal")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<InputStreamResource> exportPurchaseJournalCsv(
            @RequestParam("transactionMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(defaultValue = "false") boolean forceExport) throws Exception {
        List<TAccountsPayableSummary> summaries =
                accountsPayableSummaryService.findByTransactionMonth(transactionMonth);
        PurchaseJournalCsvService.FilterResult filtered =
                purchaseJournalCsvService.filter(summaries, forceExport);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PurchaseJournalCsvService.Result result;
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CP932)) {
            result = purchaseJournalCsvService.writeCsv(filtered.exportable, transactionMonth, w, null);
        }

        if (result.rowCount == 0) {
            // T5: 業務メッセージ (運用者にデータ条件を伝える) → FinanceBusinessException で 400 + 元メッセージ。
            throw new FinanceBusinessException(
                    "出力対象のデータがありません（MF出力ON かつ 差額 0 円以外の買掛金が存在しない）");
        }

        // CSV 出力済みマーカー（tax_included_amount / tax_excluded_amount を *_change からコピー）
        purchaseJournalCsvService.markExported(filtered.exportable);
        accountsPayableSummaryService.saveAll(filtered.exportable);
        log.info("買掛→仕入仕訳CSV DL: transactionMonth={}, rows={}, total={}, skipped={}",
                transactionMonth, result.rowCount, result.totalAmount, result.skippedSuppliers.size());

        String yyyymmdd = transactionMonth.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = forceExport ? "_UNCHECKED" : "";
        String fileName = "accounts_payable_to_purchase_journal_" + yyyymmdd + suffix + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String skippedHeader = result.skippedSuppliers.stream()
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        byte[] bytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"purchase_journal.csv\"; filename*=UTF-8''" + encoded)
                .header("X-Row-Count", String.valueOf(result.rowCount))
                .header("X-Total-Amount", result.totalAmount.toPlainString())
                .header("X-Skipped-Count", String.valueOf(result.skippedSuppliers.size()))
                .header("X-Skipped-Suppliers", skippedHeader)
                .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                .contentLength(bytes.length)
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }

    /**
     * 買掛→仕入仕訳 CSV 出力のプレビュー。件数・合計金額・除外件数をダイアログで確認するため。
     * CSV 本体は生成するが捨てる（skippedSuppliers / rowCount を得るため）。
     */
    @GetMapping("/accounts-payable/export-purchase-journal/preview")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<PurchaseJournalExportPreviewResponse> exportPurchaseJournalPreview(
            @RequestParam("transactionMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(defaultValue = "false") boolean forceExport) throws Exception {
        List<TAccountsPayableSummary> summaries =
                accountsPayableSummaryService.findByTransactionMonth(transactionMonth);
        PurchaseJournalCsvService.FilterResult filtered =
                purchaseJournalCsvService.filter(summaries, forceExport);

        PurchaseJournalCsvService.Result result;
        try (java.io.StringWriter sw = new java.io.StringWriter()) {
            result = purchaseJournalCsvService.writeCsv(filtered.exportable, transactionMonth, sw, null);
        }

        return ResponseEntity.ok(PurchaseJournalExportPreviewResponse.builder()
                .transactionMonth(transactionMonth)
                .rowCount(result.rowCount)
                .payableCount(filtered.exportable.size())
                .totalAmount(result.totalAmount)
                .nonExportableCount(filtered.nonExportableCount)
                .skippedSuppliers(result.skippedSuppliers)
                .build());
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String partnerName,
            // SF-17: closingDate のフォーマット検証 (前方一致用に YYYY/MM 単独も許容)
            //        SF-01 の DDL CHECK 制約 (^\d{4}/\d{2}/(末|\d{2})$) と整合
            @RequestParam(required = false)
                @jakarta.validation.constraints.Pattern(
                    regexp = "^\\d{4}/\\d{2}(/(末|\\d{2}))?$",
                    message = "closingDate は YYYY/MM 又は YYYY/MM/DD 又は YYYY/MM/末 形式で指定してください")
                String closingDate) {
        // SF-03: 非 admin は自 shop に強制上書き
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        List<TInvoice> invoices = tInvoiceService.findByDetailedSpecification(
                closingDate, effectiveShopNo, partnerCode, partnerName, null, null);
        return ResponseEntity.ok(invoices.stream().map(InvoiceResponse::from).collect(Collectors.toList()));
    }

    @PutMapping("/invoices/{invoiceId}/payment-date")
    public ResponseEntity<?> updatePaymentDate(
            @PathVariable Integer invoiceId,
            @Valid @RequestBody PaymentDateUpdateRequest request) {
        // SF-09: Optional 化対応
        Optional<TInvoice> opt = tInvoiceService.getInvoiceById(invoiceId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TInvoice invoice = opt.get();
        // SF-03: 単発入金日更新でも shopNo 認可ガードを実施 (IDOR 防止)
        assertShopAccess(invoice.getShopNo());
        // SF-06: paymentDate は null 許容 (null = クリア)
        invoice.setPaymentDate(request.getPaymentDate());
        tInvoiceService.saveInvoice(invoice);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PostMapping("/invoices/import")
    public ResponseEntity<?> importInvoices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer shopNo) {
        try {
            // SF-02: shopNo IDOR 修正
            //   - 非 admin (shopNo=0 以外) はリクエスト shopNo を強制上書き (= ログイン shop)
            //   - admin は要求した shopNo (null 含む) を尊重し、null の場合のみファイル名推定にフォールバック
            //   推定/上書き後の effectiveShopNo を Service に渡す
            Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
            // 非 admin が異 shop を指定したら 403 (resolveEffectiveShopNo は黙って上書きするだけなので
            //   明示的に shopNo を指定したケースだけ反論する)
            if (shopNo != null && effectiveShopNo != null && !effectiveShopNo.equals(shopNo)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "他事業部の請求データは取り込めません");
            }
            InvoiceImportResult result = invoiceImportService.importFromExcel(file, effectiveShopNo);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("請求実績インポートエラー: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("請求実績インポート失敗", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "インポート処理中にエラーが発生しました: " + e.getMessage()));
        }
    }

    @PutMapping("/invoices/bulk-payment-date")
    public ResponseEntity<?> bulkUpdatePaymentDate(@Valid @RequestBody BulkPaymentDateRequest request) {
        List<TInvoice> invoices = tInvoiceService.findByIds(request.getInvoiceIds());

        // SF-03: shopNo IDOR ガード — 異 shop の invoice を除外
        // admin (shopNo=0) は全 shop 可。非 admin は自 shop のみ。
        Integer permittedShopNo = LoginUserUtil.resolveEffectiveShopNo(null);
        List<Integer> forbiddenIds = new java.util.ArrayList<>();
        if (permittedShopNo != null) {
            invoices.removeIf(inv -> {
                boolean denied = inv.getShopNo() == null || !permittedShopNo.equals(inv.getShopNo());
                if (denied) {
                    forbiddenIds.add(inv.getInvoiceId());
                }
                return denied;
            });
        }

        List<Integer> notFoundIds = new java.util.ArrayList<>(request.getInvoiceIds());
        invoices.forEach(inv -> notFoundIds.remove(inv.getInvoiceId()));
        notFoundIds.removeAll(forbiddenIds);

        if (!forbiddenIds.isEmpty() || invoices.size() != request.getInvoiceIds().size()) {
            log.warn("入金日一括更新: 要求{}件 / 更新{}件 / 未検出{}件 / 権限外{}件",
                    request.getInvoiceIds().size(), invoices.size(),
                    notFoundIds.size(), forbiddenIds.size());
        }

        // SF-06: paymentDate は null 許容 (null = 一括クリア)
        invoices.forEach(inv -> inv.setPaymentDate(request.getPaymentDate()));
        tInvoiceService.saveAll(invoices);

        log.info("入金日一括更新完了: 更新={}件, paymentDate={}", invoices.size(), request.getPaymentDate());
        return ResponseEntity.ok(Map.of(
                "requestedCount", request.getInvoiceIds().size(),
                "updatedCount", invoices.size(),
                "notFoundIds", notFoundIds,
                "forbiddenIds", forbiddenIds));
    }

    // ---- Partner Groups ----

    @GetMapping("/partner-groups")
    public ResponseEntity<List<PartnerGroupResponse>> listPartnerGroups(
            @RequestParam(required = false) Integer shopNo) {
        // SF-03: 非 admin は自 shop に強制上書き
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        List<MPartnerGroup> groups = partnerGroupService.findByShopNo(effectiveShopNo);
        return ResponseEntity.ok(groups.stream().map(PartnerGroupResponse::from).collect(Collectors.toList()));
    }

    @PostMapping("/partner-groups")
    public ResponseEntity<PartnerGroupResponse> createPartnerGroup(
            @Valid @RequestBody PartnerGroupRequest request) {
        // SF-03: 非 admin は request.shopNo をサーバ側で自 shop に強制上書き
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(request.getShopNo());
        if (effectiveShopNo != null && !effectiveShopNo.equals(request.getShopNo())) {
            request.setShopNo(effectiveShopNo);
        }
        MPartnerGroup group = partnerGroupService.create(request);
        return ResponseEntity.ok(PartnerGroupResponse.from(group));
    }

    @PutMapping("/partner-groups/{id}")
    public ResponseEntity<PartnerGroupResponse> updatePartnerGroup(
            @PathVariable Integer id, @Valid @RequestBody PartnerGroupRequest request) {
        // SF-03: 既存 group の shopNo に対して認可ガード
        MPartnerGroup existing = partnerGroupService.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "グループが見つかりません: id=" + id);
        }
        assertShopAccess(existing.getShopNo());
        // 非 admin が他 shop へ移そうとしても弾く (= 自 shop に強制上書き)
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(request.getShopNo());
        if (effectiveShopNo != null && !effectiveShopNo.equals(request.getShopNo())) {
            request.setShopNo(effectiveShopNo);
        }
        MPartnerGroup group = partnerGroupService.update(id, request);
        return ResponseEntity.ok(PartnerGroupResponse.from(group));
    }

    @DeleteMapping("/partner-groups/{id}")
    public ResponseEntity<?> deletePartnerGroup(@PathVariable Integer id) {
        // SF-03: 既存 group の shopNo に対して認可ガード
        MPartnerGroup existing = partnerGroupService.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "グループが見つかりません: id=" + id);
        }
        assertShopAccess(existing.getShopNo());
        partnerGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 経理ワークフロー画面ステータス取得 (admin 限定)。
     * <p>SF-H04: バッチ実行履歴を含むため一般ユーザに開示しない。
     * SF-H06: 戻り型を {@link jp.co.oda32.dto.finance.AccountingStatusResponse} record に変更。
     */
    @GetMapping("/accounting-status")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<jp.co.oda32.dto.finance.AccountingStatusResponse> getAccountingStatus() {
        return ResponseEntity.ok(accountingStatusService.getStatus());
    }
}
