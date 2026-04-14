package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.domain.service.purchase.TPurchaseDetailService;
import jp.co.oda32.domain.service.purchase.TPurchaseService;
import jp.co.oda32.dto.purchase.PurchaseDetailResponse;
import jp.co.oda32.dto.purchase.PurchaseHeaderResponse;
import jp.co.oda32.dto.purchase.PurchaseListResponse;
import jp.co.oda32.dto.purchase.PurchaseListResponse.Summary;
import jp.co.oda32.dto.purchase.PurchaseListResponse.TaxRateBreakdown;
import jp.co.oda32.dto.purchase.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PurchaseController {

    private final TPurchaseService tPurchaseService;
    private final TPurchaseDetailService tPurchaseDetailService;
    private final MSupplierService mSupplierService;

    /** 買掛金集計と一致させるため除外する仕入先 (FinanceConstants 参照) */
    private static final Integer EXCLUDED_SUPPLIER_NO = jp.co.oda32.constant.FinanceConstants.EXCLUDED_SUPPLIER_NO;

    @GetMapping
    public ResponseEntity<PurchaseListResponse> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer paymentSupplierNo,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer purchaseNo) {

        List<Integer> supplierNoList = null;
        if (paymentSupplierNo != null) {
            supplierNoList = mSupplierService.findByPaymentSupplierNo(shopNo, paymentSupplierNo).stream()
                    .map(MSupplier::getSupplierNo)
                    .filter(no -> !EXCLUDED_SUPPLIER_NO.equals(no))
                    .collect(Collectors.toList());
            if (supplierNoList.isEmpty()) {
                return ResponseEntity.ok(PurchaseListResponse.builder()
                        .rows(List.of())
                        .summary(emptySummary())
                        .build());
            }
        }

        // ヘッダ取得
        List<TPurchase> purchases = tPurchaseService.search(
                shopNo, supplierNo, supplierNoList, fromDate, toDate, purchaseNo, Flag.NO);
        purchases = purchases.stream()
                .filter(p -> !EXCLUDED_SUPPLIER_NO.equals(p.getSupplierNo()))
                .sorted(Comparator.comparing(TPurchase::getPurchaseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TPurchase::getPurchaseNo, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        // 仕入先名のbulk取得
        Set<Integer> supplierNos = purchases.stream().map(TPurchase::getSupplierNo).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, MSupplier> supplierMap = supplierNos.isEmpty()
                ? Map.of()
                : mSupplierService.findBySupplierNoList(new ArrayList<>(supplierNos)).stream()
                    .collect(Collectors.toMap(MSupplier::getSupplierNo, s -> s, (a, b) -> a));

        List<PurchaseHeaderResponse> rows = purchases.stream()
                .map(p -> PurchaseHeaderResponse.from(p, supplierMap.get(p.getSupplierNo())))
                .collect(Collectors.toList());

        // 明細から税率別集計
        Summary summary = aggregateSummary(shopNo, supplierNo, supplierNoList, fromDate, toDate, purchaseNo);

        return ResponseEntity.ok(PurchaseListResponse.builder()
                .rows(rows)
                .summary(summary)
                .build());
    }

    @GetMapping("/{purchaseNo}/details")
    public ResponseEntity<List<PurchaseDetailResponse>> getDetails(@PathVariable Integer purchaseNo) {
        List<TPurchaseDetail> details = tPurchaseDetailService.findByPurchaseNo(purchaseNo);
        return ResponseEntity.ok(details.stream()
                .filter(d -> !"1".equals(d.getDelFlg()))
                .map(PurchaseDetailResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{purchaseNo}")
    public ResponseEntity<PurchaseResponse> getPurchase(@PathVariable Integer purchaseNo) {
        TPurchase purchase = tPurchaseService.getByPK(purchaseNo);
        if (purchase == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(PurchaseResponse.from(purchase));
    }

    // -------- 集計 --------

    private Summary aggregateSummary(Integer shopNo, Integer supplierNo, List<Integer> supplierNoList,
                                     LocalDate fromDate, LocalDate toDate, Integer purchaseNo) {
        // supplierNoList の単一要素ごとに detail 検索（detail 側 spec が単一 supplierNo のみ対応のため）
        List<TPurchaseDetail> details = new ArrayList<>();
        if (supplierNoList != null && !supplierNoList.isEmpty()) {
            for (Integer sn : supplierNoList) {
                if (EXCLUDED_SUPPLIER_NO.equals(sn)) continue;
                details.addAll(tPurchaseDetailService.find(
                        shopNo, null, purchaseNo, null, null, null, null, fromDate, toDate, sn, null, Flag.NO));
            }
        } else {
            details.addAll(tPurchaseDetailService.find(
                    shopNo, null, purchaseNo, null, null, null, null, fromDate, toDate, supplierNo, null, Flag.NO));
        }
        // 除外
        details = details.stream()
                .filter(d -> {
                    TPurchase p = d.getTPurchase();
                    return p == null || !EXCLUDED_SUPPLIER_NO.equals(p.getSupplierNo());
                })
                .collect(Collectors.toList());

        // 税率別 group
        Map<BigDecimal, List<TPurchaseDetail>> byRate = details.stream()
                .collect(Collectors.groupingBy(d -> d.getTaxRate() != null ? d.getTaxRate() : BigDecimal.ZERO,
                        LinkedHashMap::new, Collectors.toList()));

        List<TaxRateBreakdown> breakdowns = new ArrayList<>();
        BigDecimal totalExc = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalInc = BigDecimal.ZERO;
        for (var e : byRate.entrySet()) {
            BigDecimal exc = e.getValue().stream()
                    .map(d -> d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal rate = e.getKey() != null ? e.getKey() : BigDecimal.ZERO;
            BigDecimal inc = e.getValue().stream()
                    .map(d -> includeTaxOrFallback(d, rate))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = inc.subtract(exc);
            breakdowns.add(TaxRateBreakdown.builder()
                    .taxRate(e.getKey())
                    .rows(e.getValue().size())
                    .amountExcTax(exc)
                    .taxAmount(tax)
                    .amountIncTax(inc)
                    .build());
            totalExc = totalExc.add(exc);
            totalTax = totalTax.add(tax);
            totalInc = totalInc.add(inc);
        }
        breakdowns.sort(Comparator.comparing(TaxRateBreakdown::getTaxRate, Comparator.nullsLast(Comparator.reverseOrder())));

        return Summary.builder()
                .totalRows(details.size())
                .totalAmountExcTax(totalExc)
                .totalTaxAmount(totalTax)
                .totalAmountIncTax(totalInc)
                .byTaxRate(breakdowns)
                .build();
    }

    /** include_tax_subtotal が NULL の場合は subtotal × (1 + taxRate/100) で算出（円未満切り捨て）。 */
    private static BigDecimal includeTaxOrFallback(TPurchaseDetail d, BigDecimal rate) {
        if (d.getIncludeTaxSubtotal() != null) {
            return d.getIncludeTaxSubtotal();
        }
        BigDecimal exc = d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO;
        BigDecimal multiplier = BigDecimal.ONE.add(
                (rate != null ? rate : BigDecimal.ZERO).divide(BigDecimal.valueOf(100)));
        return exc.multiply(multiplier).setScale(0, java.math.RoundingMode.DOWN);
    }

    private Summary emptySummary() {
        return Summary.builder()
                .totalRows(0)
                .totalAmountExcTax(BigDecimal.ZERO)
                .totalTaxAmount(BigDecimal.ZERO)
                .totalAmountIncTax(BigDecimal.ZERO)
                .byTaxRate(List.of())
                .build();
    }
}
