package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import jp.co.oda32.dto.finance.MfHealthResponse;
import jp.co.oda32.dto.finance.MfHealthResponse.AnomalyStats;
import jp.co.oda32.dto.finance.MfHealthResponse.CacheStats;
import jp.co.oda32.dto.finance.MfHealthResponse.MfOauthStatus;
import jp.co.oda32.dto.finance.MfHealthResponse.ShopCacheInfo;
import jp.co.oda32.dto.finance.MfHealthResponse.SummaryStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MF 連携ヘルスチェックサービス (軸 E)。
 * <p>
 * MF OAuth 状態 / 買掛金 summary 集計 / anomaly / journals cache を 1 レスポンスで返す。
 * <p>
 * 設計書: claudedocs/design-supplier-balances-health.md §4
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MfHealthCheckService {

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final TAccountsPayableSummaryRepository summaryRepository;

    public MfHealthResponse check(Integer shopNo) {
        return MfHealthResponse.builder()
                .checkedAt(Instant.now())
                .shopNo(shopNo)
                .mfOauth(buildOauthStatus())
                .summary(buildSummaryStats(shopNo))
                .anomalies(buildAnomalyStats(shopNo))
                .cache(buildCacheStats())
                .build();
    }

    private MfOauthStatus buildOauthStatus() {
        MfTokenStatus s = mfOauthService.getStatus();
        Long expiresInHours = null;
        if (s.connected() && s.expiresAt() != null) {
            long secs = Duration.between(Instant.now(), s.expiresAt()).getSeconds();
            expiresInHours = Math.max(0, secs / 3600);
        }
        return MfOauthStatus.builder()
                .connected(s.connected())
                .tokenExpiresAt(s.expiresAt())
                .scope(s.scope())
                .expiresInHours(expiresInHours)
                .build();
    }

    private SummaryStats buildSummaryStats(Integer shopNo) {
        Optional<LocalDate> latest = summaryRepository.findLatestTransactionMonth(shopNo);
        if (latest.isEmpty()) {
            return SummaryStats.builder()
                    .latestMonth(null)
                    .totalCount(0L).verifiedCount(0L).unverifiedCount(0L).mfExportEnabledCount(0L)
                    .build();
        }
        LocalDate m = latest.get();
        long total = summaryRepository.countByShopNoAndTransactionMonth(shopNo, m);
        long unverified = summaryRepository.countByShopNoAndTransactionMonthAndVerificationResult(shopNo, m, 0);
        long mfExport = summaryRepository.countByShopNoAndTransactionMonthAndMfExportEnabled(shopNo, m, true);
        return SummaryStats.builder()
                .latestMonth(m)
                .totalCount(total)
                .verifiedCount(total - unverified)
                .unverifiedCount(unverified)
                .mfExportEnabledCount(mfExport)
                .build();
    }

    private AnomalyStats buildAnomalyStats(Integer shopNo) {
        long negative = summaryRepository.countNegativeClosings(shopNo);
        Optional<LocalDate> latest = summaryRepository.findLatestTransactionMonth(shopNo);
        long unverified = latest
                .map(m -> summaryRepository.countByShopNoAndTransactionMonthAndVerificationResult(shopNo, m, 0))
                .orElse(0L);
        // verifyDiff / continuityBreak / monthGap は Phase B''(light) 以降の anomaly 検出ロジックが
        // 買掛帳画面側 (PayableLedgerService) で supplier 単位に出すもの。shop 単位の集計は未実装のため
        // 当面は 0 を返す (MEMORY.md の anomaly 種別参照)。次フェーズで PayableAnomalyCounter を util 化予定。
        return AnomalyStats.builder()
                .negativeClosingCount(negative)
                .unverifiedCount(unverified)
                .verifyDiffCount(0L)
                .continuityBreakCount(0L)
                .monthGapCount(0L)
                .build();
    }

    private CacheStats buildCacheStats() {
        List<MfJournalCacheService.ShopStats> stats = journalCache.getStats();
        List<ShopCacheInfo> infos = new ArrayList<>();
        for (MfJournalCacheService.ShopStats s : stats) {
            infos.add(ShopCacheInfo.builder()
                    .shopNo(s.shopNo())
                    .monthsCount(s.monthsCount())
                    .oldestFetchedAt(s.oldestFetchedAt())
                    .newestFetchedAt(s.newestFetchedAt())
                    .build());
        }
        return CacheStats.builder().cachedShops(infos).build();
    }
}
