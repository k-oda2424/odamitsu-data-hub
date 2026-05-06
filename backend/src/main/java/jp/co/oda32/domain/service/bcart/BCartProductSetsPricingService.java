package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.repository.bcart.BCartProductSetsRepository;
import jp.co.oda32.dto.bcart.BCartProductSetPricingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * B-CART商品セットの価格・配送サイズ更新Service。
 * 編集と変更履歴の記録をトランザクション内で実行する。
 */
@Service
@RequiredArgsConstructor
public class BCartProductSetsPricingService {

    private final BCartProductSetsRepository setsRepository;
    private final BCartChangeHistoryService changeHistoryService;

    @Transactional
    public Optional<BCartProductSets> update(Long setId, BCartProductSetPricingRequest request, Integer userNo) {
        return setsRepository.findById(setId).map(set -> {
            boolean changed = false;

            if (request.getUnitPrice() != null
                    && !equalsBigDecimal(set.getUnitPrice(), request.getUnitPrice())) {
                changeHistoryService.recordChange(
                        "PRODUCT_SET", setId, "UNIT_PRICE", "unit_price",
                        toPlainString(set.getUnitPrice()), toPlainString(request.getUnitPrice()),
                        null, userNo);
                set.setUnitPrice(request.getUnitPrice());
                changed = true;
            }

            if (request.getShippingSize() != null
                    && !equalsBigDecimal(set.getShippingSize(), request.getShippingSize())) {
                changeHistoryService.recordChange(
                        "PRODUCT_SET", setId, "SHIPPING_SIZE", "shipping_size",
                        toPlainString(set.getShippingSize()), toPlainString(request.getShippingSize()),
                        null, userNo);
                set.setShippingSize(request.getShippingSize());
                changed = true;
            }

            if (changed) {
                set.setBCartPriceReflected(false);
                return setsRepository.save(set);
            }
            return set;
        });
    }

    private boolean equalsBigDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private String toPlainString(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
