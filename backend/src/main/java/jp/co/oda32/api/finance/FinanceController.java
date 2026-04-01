package jp.co.oda32.api.finance;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.dto.finance.AccountsPayableResponse;
import jp.co.oda32.dto.finance.InvoiceResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final TAccountsPayableSummaryService accountsPayableSummaryService;
    private final TInvoiceService tInvoiceService;

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

    @Data
    static class PaymentDateUpdateRequest {
        private LocalDate paymentDate;
    }
}
