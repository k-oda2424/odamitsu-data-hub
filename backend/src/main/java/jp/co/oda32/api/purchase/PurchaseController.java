package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.service.purchase.TPurchaseService;
import jp.co.oda32.dto.purchase.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final TPurchaseService tPurchaseService;

    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> listPurchases(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer companyNo) {
        List<TPurchase> purchases = tPurchaseService.find(shopNo, companyNo, null, null, null, Flag.NO);
        return ResponseEntity.ok(purchases.stream().map(PurchaseResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/{purchaseNo}")
    public ResponseEntity<PurchaseResponse> getPurchase(@PathVariable Integer purchaseNo) {
        TPurchase purchase = tPurchaseService.getByPK(purchaseNo);
        if (purchase == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PurchaseResponse.from(purchase));
    }
}
