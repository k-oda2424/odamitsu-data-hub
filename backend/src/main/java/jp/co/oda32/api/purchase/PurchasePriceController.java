package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import jp.co.oda32.dto.purchase.PurchasePriceResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/purchase-prices")
@RequiredArgsConstructor
@Validated
public class PurchasePriceController {

    private final MPurchasePriceService mPurchasePriceService;

    @GetMapping
    public ResponseEntity<List<PurchasePriceResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) @Pattern(regexp = "standard|partner|all", message = "scopeはstandard/partner/allのいずれかです") String scope) {
        List<MPurchasePrice> prices = mPurchasePriceService.find(
                shopNo, null, goodsCode, goodsName, null, supplierNo, null, Flag.NO);

        // scope filter: standard / partner / all (default: all)
        if ("standard".equals(scope)) {
            prices = prices.stream()
                    .filter(p -> (p.getPartnerNo() == null || p.getPartnerNo() == 0)
                            && (p.getDestinationNo() == null || p.getDestinationNo() == 0))
                    .collect(Collectors.toList());
        } else if ("partner".equals(scope)) {
            prices = prices.stream()
                    .filter(p -> (p.getPartnerNo() != null && p.getPartnerNo() != 0)
                            || (p.getDestinationNo() != null && p.getDestinationNo() != 0))
                    .collect(Collectors.toList());
        }

        // (goodsNo, partnerNo, destinationNo) で重複排除し、最新のpurchasePriceNoを採用
        // → 標準価格と特値価格が両方表示される
        List<PurchasePriceResponse> result = prices.stream()
                .sorted(Comparator.comparing(MPurchasePrice::getPurchasePriceNo,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toMap(
                        p -> (p.getGoodsNo() == null ? 0 : p.getGoodsNo()) + "_"
                                + (p.getPartnerNo() == null ? 0 : p.getPartnerNo()) + "_"
                                + (p.getDestinationNo() == null ? 0 : p.getDestinationNo()),
                        PurchasePriceResponse::from,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        return ResponseEntity.ok(result);
    }
}
