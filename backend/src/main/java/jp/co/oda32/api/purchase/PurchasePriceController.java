package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import jp.co.oda32.dto.purchase.PurchasePriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(prices.stream().map(PurchasePriceResponse::from).collect(Collectors.toList()));
    }
}
