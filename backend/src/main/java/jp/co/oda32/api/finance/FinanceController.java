package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.AccountingStatusService;
import jp.co.oda32.domain.service.finance.InvoiceImportService;
import jp.co.oda32.domain.service.finance.MPartnerGroupService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    private final TAccountsPayableSummaryService accountsPayableSummaryService;
    private final TInvoiceService tInvoiceService;
    private final InvoiceImportService invoiceImportService;
    private final MPartnerGroupService partnerGroupService;
    private final AccountingStatusService accountingStatusService;
    private final MPaymentSupplierService mPaymentSupplierService;

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
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "他ショップのデータにはアクセスできません");
        }
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
