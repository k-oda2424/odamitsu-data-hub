package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import jp.co.oda32.dto.purchase.PurchasePriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/purchase-prices")
@RequiredArgsConstructor
public class PurchasePriceController {

    private final MPurchasePriceService mPurchasePriceService;

    @GetMapping
    public ResponseEntity<List<PurchasePriceResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String goodsName,
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) Integer supplierNo) {
        List<MPurchasePrice> prices = mPurchasePriceService.find(
                shopNo, null, goodsCode, goodsName, null, supplierNo, null, Flag.NO);

        // 同一商品（goodsNo）の重複を排除し、最新のpurchasePriceNoを採用
        List<PurchasePriceResponse> result = prices.stream()
                .sorted(Comparator.comparingInt(MPurchasePrice::getPurchasePriceNo).reversed())
                .collect(Collectors.toMap(
                        MPurchasePrice::getGoodsNo,
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
