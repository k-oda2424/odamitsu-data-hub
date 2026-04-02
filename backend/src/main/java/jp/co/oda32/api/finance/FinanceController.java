package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.InvoiceImportService;
import jp.co.oda32.domain.service.finance.MPartnerGroupService;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.dto.finance.AccountsPayableResponse;
import jp.co.oda32.dto.finance.BulkPaymentDateRequest;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import jp.co.oda32.dto.finance.InvoiceResponse;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import jp.co.oda32.dto.finance.PartnerGroupResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final TAccountsPayableSummaryService accountsPayableSummaryService;
    private final TInvoiceService tInvoiceService;
    private final InvoiceImportService invoiceImportService;
    private final MPartnerGroupService partnerGroupService;

    // TODO: shopNo/supplierNo でのフィルタリングをService層（Specification）で実装する
    @GetMapping("/accounts-payable")
    public ResponseEntity<List<AccountsPayableResponse>> listAccountsPayable(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer supplierNo) {
        List<TAccountsPayableSummary> list = accountsPayableSummaryService.findAll();
        return ResponseEntity.ok(list.stream()
                .filter(ap -> shopNo == null || shopNo.equals(ap.getShopNo()))
                .filter(ap -> supplierNo == null || supplierNo.equals(ap.getSupplierNo()))
                .map(AccountsPayableResponse::from)
                .collect(Collectors.toList()));
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
            @RequestBody PaymentDateUpdateRequest request) {
        TInvoice invoice = tInvoiceService.getInvoiceById(invoiceId);
        if (invoice == null) {
            return ResponseEntity.notFound().build();
        }
        invoice.setPaymentDate(request.getPaymentDate());
        tInvoiceService.saveInvoice(invoice);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PostMapping("/invoices/import")
    public ResponseEntity<?> importInvoices(@RequestParam("file") MultipartFile file) {
        try {
            InvoiceImportResult result = invoiceImportService.importFromExcel(file);
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

    @Data
    static class PaymentDateUpdateRequest {
        private LocalDate paymentDate;
    }
}
