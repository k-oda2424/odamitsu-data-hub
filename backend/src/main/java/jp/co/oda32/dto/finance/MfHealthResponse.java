package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * MF 連携ヘルスチェック レスポンス (軸 E)。
 * <p>
 * MF OAuth 状態 / 買掛金サマリ集計 / anomaly / journals キャッシュ状態を 1 画面で返す。
 * <p>
 * 設計書: claudedocs/design-supplier-balances-health.md §4
 *
 * @since 2026-04-23
 */
@Data
@Builder
public class MfHealthResponse {
    private Instant checkedAt;
    private Integer shopNo;
    private MfOauthStatus mfOauth;
    private SummaryStats summary;
    private AnomalyStats anomalies;
    private CacheStats cache;

    @Data
    @Builder
    public static class MfOauthStatus {
        private Boolean connected;
        private Instant tokenExpiresAt;
        private String scope;
        /** token 残り時間 (時間単位)。null なら未接続 */
        private Long expiresInHours;
    }

    @Data
    @Builder
    public static class SummaryStats {
        private LocalDate latestMonth;
        private Long totalCount;
        private Long verifiedCount;
        private Long unverifiedCount;
        private Long mfExportEnabledCount;
    }

    @Data
    @Builder
    public static class AnomalyStats {
        private Long negativeClosingCount;
        private Long unverifiedCount;
        private Long verifyDiffCount;
        private Long continuityBreakCount;
        private Long monthGapCount;
    }

    @Data
    @Builder
    public static class CacheStats {
        private List<ShopCacheInfo> cachedShops;
    }

    @Data
    @Builder
    public static class ShopCacheInfo {
        private Integer shopNo;
        private Integer monthsCount;
        private Instant oldestFetchedAt;
        private Instant newestFetchedAt;
    }
}
