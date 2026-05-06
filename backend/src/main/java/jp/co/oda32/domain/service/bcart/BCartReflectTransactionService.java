package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.repository.bcart.BCartChangeHistoryRepository;
import jp.co.oda32.domain.repository.bcart.BCartProductSetsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B-CART反映成功時の DB 更新を public @Transactional でラップする専用 Bean。
 *
 * `BCartProductSetsReflectService` のループ内から自己呼出すると CGLIB プロキシが
 * 効かないため、別 Bean に切り出してトランザクション境界を確保する。
 */
@Service
@RequiredArgsConstructor
public class BCartReflectTransactionService {

    private final BCartChangeHistoryRepository historyRepository;
    private final BCartProductSetsRepository setsRepository;

    /**
     * 指定された history ID リストを反映済みにマークし、
     * 商品セットの `b_cart_price_reflected` を true に戻す。
     *
     * @param productSetId 対象商品セットID
     * @param historyIds   反映成功した history の ID リスト（aggregate 時に保持済み）
     */
    @Transactional
    public void markReflected(Long productSetId, List<Long> historyIds) {
        if (historyIds != null && !historyIds.isEmpty()) {
            historyRepository.markReflectedByIds(historyIds);
        }
        setsRepository.findById(productSetId).ifPresent(s -> {
            s.setBCartPriceReflected(true);
            setsRepository.save(s);
        });
    }
}
