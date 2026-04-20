package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.AccountingStatusService;
import jp.co.oda32.domain.service.finance.InvoiceImportService;
import jp.co.oda32.domain.service.finance.MPartnerGroupService;
import jp.co.oda32.domain.service.finance.PurchaseJournalCsvService;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.AccountsPayableResponse;
import jp.co.oda32.dto.finance.AccountsPayableSummaryResponse;
import jp.co.oda32.dto.finance.AccountsPayableVerifyRequest;
import jp.co.oda32.dto.finance.BulkPaymentDateRequest;
import jp.co.oda32.dto.finance.MfExportToggleRequest;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import jp.co.oda32.dto.finance.InvoiceResponse;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import jp.co.oda32.dto.finance.PartnerGroupResponse;
import jp.co.oda32.dto.finance.PaymentDateUpdateRequest;
import jp.co.oda32.dto.finance.PurchaseJournalExportPreviewResponse;
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
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class FinanceController {

    private static final Charset CP932 = Charset.forName("windows-31j");

    private final TAccountsPayableSummaryService accountsPayableSummaryService;
    private final TInvoiceService tInvoiceService;
    private final InvoiceImportService invoiceImportService;
    private final MPartnerGroupService partnerGroupService;
    private final AccountingStatusService accountingStatusService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final PurchaseJournalCsvService purchaseJournalCsvService;

    @GetMapping("/accounts-payable")
    public ResponseEntity<Page<AccountsPayableResponse>> listAccountsPayable(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(required = false) String verificationFilter,
            @PageableDefault(size = 50, sort = "supplierCode", direction = Sort.Direction.ASC) Pageable pageable) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        Page<TAccountsPayableSummary> page = accountsPayableSummaryService.findPaged(
                effectiveShopNo, supplierNo, transactionMonth, verificationFilter, pageable);

        Set<Integer> supplierNos = page.getContent().stream()
                .map(TAccountsPayableSummary::getSupplierNo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, MPaymentSupplier> psMap = mPaymentSupplierService.findAllByPaymentSupplierNos(supplierNos).stream()
                .collect(Collectors.toMap(MPaymentSupplier::getPaymentSupplierNo, p -> p, (a, b) -> a));

        return ResponseEntity.ok(page.map(ap -> AccountsPayableResponse.from(ap, psMap.get(ap.getSupplierNo()))));
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
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
    @PreAuthorize("hasRole('ADMIN')")
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
            @RequestParam(required = false) String closingDate) {
        List<TInvoice> invoices = tInvoiceService.findByDetailedSpecification(
                closingDate, shopNo, partnerCode, partnerName, null, null);
        return ResponseEntity.ok(invoices.stream().map(InvoiceResponse::from).collect(Collectors.toList()));
    }

    @PutMapping("/invoices/{invoiceId}/payment-date")
    public ResponseEntity<?> updatePaymentDate(
            @PathVariable Integer invoiceId,
            @Valid @RequestBody PaymentDateUpdateRequest request) {
        TInvoice invoice = tInvoiceService.getInvoiceById(invoiceId);
        if (invoice == null) {
            return ResponseEntity.notFound().build();
        }
        invoice.setPaymentDate(request.getPaymentDate());
        tInvoiceService.saveInvoice(invoice);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PostMapping("/invoices/import")
    public ResponseEntity<?> importInvoices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer shopNo) {
        try {
            InvoiceImportResult result = invoiceImportService.importFromExcel(file, shopNo);
            return ResponseEntity.ok(result);
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
        if (invoices.size() != request.getInvoiceIds().size()) {
            log.warn("入金日一括更新: 要求{}件中{}件が見つかりました",
                    request.getInvoiceIds().size(), invoices.size());
        }
        invoices.forEach(inv -> inv.setPaymentDate(request.getPaymentDate()));
        tInvoiceService.saveAll(invoices);
        log.info("入金日一括更新: {}件, paymentDate={}", invoices.size(), request.getPaymentDate());
        return ResponseEntity.ok(Map.of("updatedCount", invoices.size()));
    }

    // ---- Partner Groups ----

    @GetMapping("/partner-groups")
    public ResponseEntity<List<PartnerGroupResponse>> listPartnerGroups(
            @RequestParam(required = false) Integer shopNo) {
        List<MPartnerGroup> groups = partnerGroupService.findByShopNo(shopNo);
        return ResponseEntity.ok(groups.stream().map(PartnerGroupResponse::from).collect(Collectors.toList()));
    }

    @PostMapping("/partner-groups")
    public ResponseEntity<PartnerGroupResponse> createPartnerGroup(
            @Valid @RequestBody PartnerGroupRequest request) {
        MPartnerGroup group = partnerGroupService.create(request);
        return ResponseEntity.ok(PartnerGroupResponse.from(group));
    }

    @PutMapping("/partner-groups/{id}")
    public ResponseEntity<PartnerGroupResponse> updatePartnerGroup(
            @PathVariable Integer id, @Valid @RequestBody PartnerGroupRequest request) {
        MPartnerGroup group = partnerGroupService.update(id, request);
        return ResponseEntity.ok(PartnerGroupResponse.from(group));
    }

    @DeleteMapping("/partner-groups/{id}")
    public ResponseEntity<?> deletePartnerGroup(@PathVariable Integer id) {
        partnerGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounting-status")
    public ResponseEntity<Map<String, Object>> getAccountingStatus() {
        return ResponseEntity.ok(accountingStatusService.getStatus());
    }
}
