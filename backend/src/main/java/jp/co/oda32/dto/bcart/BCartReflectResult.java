package jp.co.oda32.dto.bcart;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * B-CART反映処理の結果。
 */
@Data
public class BCartReflectResult {
    private int succeeded;
    private int failed;
    private int skipped;
    private final List<Item> results = new ArrayList<>();

    public void addSuccess(Long productSetId) {
        succeeded++;
        results.add(Item.of(productSetId, "SUCCESS", null));
    }

    public void addFailure(Long productSetId, String errorMessage) {
        failed++;
        results.add(Item.of(productSetId, "FAILED", errorMessage));
    }

    public void addSkipped(Long productSetId, String reason) {
        skipped++;
        results.add(Item.of(productSetId, "SKIPPED", reason));
    }

    @Data
    public static class Item {
        private Long productSetId;
        private String status;          // SUCCESS / FAILED / SKIPPED
        private String message;

        public static Item of(Long productSetId, String status, String message) {
            Item i = new Item();
            i.productSetId = productSetId;
            i.status = status;
            i.message = message;
            return i;
        }
    }
}
