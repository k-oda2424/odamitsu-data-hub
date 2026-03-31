package jp.co.oda32.api.finance;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.dto.finance.AccountsPayableResponse;
import jp.co.oda32.dto.finance.InvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @RequestParam(required = false) String closingDate) {
        List<TInvoice> invoices = tInvoiceService.findBySpecification(closingDate, shopNo, partnerCode);
        return ResponseEntity.ok(invoices.stream().map(InvoiceResponse::from).collect(Collectors.toList()));
    }
}
