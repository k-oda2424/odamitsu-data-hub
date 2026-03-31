package jp.co.oda32.domain.model.bcart.productSets;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class StockParent {
    private Map<String, Integer> stockParentMap;
}