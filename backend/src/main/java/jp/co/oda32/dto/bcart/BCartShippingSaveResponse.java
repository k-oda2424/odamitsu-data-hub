package jp.co.oda32.dto.bcart;

import java.util.List;

/**
 * 出荷情報 一括更新のレスポンス。
 * <p>
 * skippedIds: B-CART CSV 出力済み＋発送済で更新対象外となった bCartLogisticsId 一覧。
 * クライアントは「一部がサイレント無視された」ことを検知でき、画面で警告表示できる。
 */
public record BCartShippingSaveResponse(
        int updatedCount,
        List<Long> skippedIds
) {
}
