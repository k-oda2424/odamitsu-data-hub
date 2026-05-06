package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.repository.bcart.BCartChangeHistoryRepository;
import jp.co.oda32.domain.repository.bcart.BCartProductSetsRepository;
import jp.co.oda32.domain.repository.bcart.BCartProductsRepository;
import jp.co.oda32.dto.bcart.BCartPendingChangeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 未反映変更点の集約クエリ Service。
 *
 * b_cart_change_history を主源泉とし、(target_id, field_name) でグルーピング。
 * 各グループは最古 before + 最新 after に集約。
 * 結果に商品名・セット名を結合して返す。
 *
 * 件数上限 500（暴発防止）。
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class BCartPendingChangesQueryService {

    private static final int LIMIT = 500;
    private static final List<String> TARGET_FIELDS = List.of("unit_price", "shipping_size");

    private final BCartChangeHistoryRepository historyRepository;
    private final BCartProductSetsRepository setsRepository;
    private final BCartProductsRepository productsRepository;

    @Transactional(readOnly = true)
    public List<BCartPendingChangeResponse> findPendingChanges() {
        // 1. 未反映 history 取得（ASC ソートで earliest before / latest after を順序判定可能に）
        List<BCartChangeHistory> rows = historyRepository.findBybCartReflectedIsFalseAndTargetType("PRODUCT_SET")
                .stream()
                .filter(h -> TARGET_FIELDS.contains(h.getFieldName()))
                .sorted(Comparator.comparing(BCartChangeHistory::getChangedAt))
                .toList();

        // 2. target_id で group 化
        Map<Long, List<BCartChangeHistory>> bySet = rows.stream()
                .collect(Collectors.groupingBy(BCartChangeHistory::getTargetId, LinkedHashMap::new, Collectors.toList()));

        // 件数制限
        List<Long> setIds;
        if (bySet.size() > LIMIT) {
            log.warn("Pending changes exceed limit: total={}, returning {} only", bySet.size(), LIMIT);
            setIds = bySet.keySet().stream().limit(LIMIT).toList();
        } else {
            setIds = new ArrayList<>(bySet.keySet());
        }

        if (setIds.isEmpty()) return List.of();

        // 3. 関連 product_sets + products を一括取得
        Map<Long, BCartProductSets> setMap = setsRepository.findAllById(setIds).stream()
                .collect(Collectors.toMap(BCartProductSets::getId, s -> s));

        List<Integer> productIds = setMap.values().stream()
                .map(s -> s.getProductId() != null ? s.getProductId().intValue() : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, BCartProducts> productMap = productIds.isEmpty()
                ? new HashMap<>()
                : productsRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(BCartProducts::getId, p -> p));

        // 4. レスポンス組立
        List<BCartPendingChangeResponse> responses = new ArrayList<>();
        for (Long setId : setIds) {
            BCartProductSets set = setMap.get(setId);
            if (set == null) continue;
            BCartProducts product = set.getProductId() != null
                    ? productMap.get(set.getProductId().intValue())
                    : null;

            // field_name で group → earliest before / latest after
            Map<String, List<BCartChangeHistory>> byField = bySet.get(setId).stream()
                    .collect(Collectors.groupingBy(BCartChangeHistory::getFieldName, LinkedHashMap::new, Collectors.toList()));

            List<BCartPendingChangeResponse.Change> changes = new ArrayList<>();
            LocalDateTime maxChangedAt = null;
            for (Map.Entry<String, List<BCartChangeHistory>> entry : byField.entrySet()) {
                List<BCartChangeHistory> groupRows = entry.getValue();
                BCartChangeHistory first = groupRows.get(0);
                BCartChangeHistory last = groupRows.get(groupRows.size() - 1);
                LocalDateTime changedAt = last.getChangedAt() != null ? last.getChangedAt().toLocalDateTime() : null;
                if (maxChangedAt == null || (changedAt != null && changedAt.isAfter(maxChangedAt))) {
                    maxChangedAt = changedAt;
                }
                changes.add(BCartPendingChangeResponse.Change.builder()
                        .field(entry.getKey())
                        .before(first.getBeforeValue())
                        .after(last.getAfterValue())
                        .changedAt(changedAt)
                        .build());
            }

            responses.add(BCartPendingChangeResponse.builder()
                    .productSetId(setId)
                    .productId(set.getProductId())
                    .productName(product != null ? product.getName() : null)
                    .setName(set.getName())
                    .productNo(set.getProductNo())
                    .janCode(set.getJanCode())
                    .changes(changes)
                    .lastChangedAt(maxChangedAt)
                    .build());
        }

        // 最新変更日時 DESC でソート
        responses.sort((a, b) -> {
            if (a.getLastChangedAt() == null && b.getLastChangedAt() == null) return 0;
            if (a.getLastChangedAt() == null) return 1;
            if (b.getLastChangedAt() == null) return -1;
            return b.getLastChangedAt().compareTo(a.getLastChangedAt());
        });

        return responses;
    }

    @Transactional(readOnly = true)
    public long countPendingProductSets() {
        return historyRepository.findUnreflectedProductSetIds(TARGET_FIELDS).size();
    }
}
