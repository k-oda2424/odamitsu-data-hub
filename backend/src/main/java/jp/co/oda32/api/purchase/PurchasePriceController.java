package jp.co.oda32.api.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.purchase.MPurchasePriceService;
import jp.co.oda32.dto.purchase.PurchasePriceCreateRequest;
import jp.co.oda32.dto.purchase.PurchasePriceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/purchase-prices")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
public class PurchasePriceController {

    private final MPurchasePriceService mPurchasePriceService;
    private final MGoodsService mGoodsService;
    private final jp.co.oda32.domain.service.login.LoginUserService loginUserService;

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

    @GetMapping("/{purchasePriceNo}")
    public ResponseEntity<PurchasePriceResponse> get(@PathVariable Integer purchasePriceNo) {
        MPurchasePrice pp = mPurchasePriceService.getByPK(purchasePriceNo);
        if (pp == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(PurchasePriceResponse.from(pp));
    }

    @PostMapping
    public ResponseEntity<PurchasePriceResponse> create(@Valid @RequestBody PurchasePriceCreateRequest request) throws Exception {
        if (checkShopDenied(request.getShopNo())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        MPurchasePrice pp = buildEntity(null, request);
        MPurchasePrice saved = mPurchasePriceService.save(pp);
        return ResponseEntity.status(HttpStatus.CREATED).body(PurchasePriceResponse.from(saved));
    }

    @PutMapping("/{purchasePriceNo}")
    public ResponseEntity<PurchasePriceResponse> update(
            @PathVariable Integer purchasePriceNo,
            @Valid @RequestBody PurchasePriceCreateRequest request) throws Exception {
        MPurchasePrice existing = mPurchasePriceService.getByPK(purchasePriceNo);
        if (existing == null) return ResponseEntity.notFound().build();
        if (checkShopDenied(existing.getShopNo())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        MPurchasePrice pp = buildEntity(purchasePriceNo, request);
        MPurchasePrice saved = mPurchasePriceService.save(pp);
        return ResponseEntity.ok(PurchasePriceResponse.from(saved));
    }

    @DeleteMapping("/{purchasePriceNo}")
    public ResponseEntity<Void> delete(@PathVariable Integer purchasePriceNo) throws Exception {
        MPurchasePrice pp = mPurchasePriceService.getByPK(purchasePriceNo);
        if (pp == null) return ResponseEntity.notFound().build();
        if (checkShopDenied(pp.getShopNo())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        pp.setDelFlg(Flag.YES.getValue());
        mPurchasePriceService.save(pp);
        return ResponseEntity.noContent().build();
    }

    private boolean checkShopDenied(Integer targetShopNo) {
        try {
            Integer userShopNo = loginUserService.getLoginUser().getShopNo();
            return userShopNo != null && userShopNo != 0 && !userShopNo.equals(targetShopNo);
        } catch (Exception e) {
            return false;
        }
    }

    private MPurchasePrice buildEntity(Integer purchasePriceNo, PurchasePriceCreateRequest req) {
        BigDecimal goodsPrice;
        BigDecimal includeTaxGoodsPrice;
        BigDecimal taxRate = req.getTaxRate() != null ? req.getTaxRate() : BigDecimal.TEN;
        BigDecimal multiplyTaxRate = BigDecimal.ONE.add(taxRate.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));

        if (req.isIncludeTaxFlg()) {
            includeTaxGoodsPrice = req.getGoodsPrice();
            goodsPrice = req.getGoodsPrice().divide(multiplyTaxRate, 2, RoundingMode.DOWN);
        } else {
            goodsPrice = req.getGoodsPrice();
            includeTaxGoodsPrice = req.getGoodsPrice().multiply(multiplyTaxRate).setScale(2, RoundingMode.UP);
        }

        int taxCategory = 0;
        MGoods goods = mGoodsService.getByGoodsNo(req.getGoodsNo());
        if (goods != null) {
            taxCategory = goods.isApplyReducedTaxRate() ? 1 : 0;
        }

        return MPurchasePrice.builder()
                .purchasePriceNo(purchasePriceNo)
                .shopNo(req.getShopNo())
                .goodsNo(req.getGoodsNo())
                .supplierNo(req.getSupplierNo())
                .partnerNo(req.getPartnerNo() != null ? req.getPartnerNo() : 0)
                .destinationNo(req.getDestinationNo() != null ? req.getDestinationNo() : 0)
                .goodsPrice(goodsPrice)
                .includeTaxGoodsPrice(includeTaxGoodsPrice)
                .taxRate(taxRate)
                .taxCategory(taxCategory)
                .includeTaxFlg(req.isIncludeTaxFlg() ? "1" : "0")
                .periodFrom(req.getPeriodFrom())
                .periodTo(req.getPeriodTo())
                .note(req.getNote())
                .delFlg(Flag.NO.getValue())
                .build();
    }
}
