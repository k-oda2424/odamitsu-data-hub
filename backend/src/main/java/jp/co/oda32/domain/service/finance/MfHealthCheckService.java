package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfApiClient;
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
    private final MfApiClient mfApiClient;

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
        // SF-22: 軽量 ping (listAccounts) で実通信可否を判定。
        // 未接続 (connected=false) なら null のまま (skip)。
        Boolean apiReachable = null;
        if (Boolean.TRUE.equals(s.connected())) {
            apiReachable = pingMfApi();
        }
        return MfOauthStatus.builder()
                .connected(s.connected())
                .tokenExpiresAt(s.expiresAt())
                .scope(s.scope())
                .expiresInHours(expiresInHours)
                .apiReachable(apiReachable)
                .build();
    }

    /**
     * SF-22: MF API への軽量 ping。listAccounts (キャッシュ向きの小さいレスポンス) を 1 回呼び、
     * 例外が出たら false を返す。connected=true でも実際にアクセスできないケース
     * (scope 不足 / refresh 失敗 / MF 側障害) を切り分けるための健康診断。
     * <p>
     * <strong>MA-03</strong>: token refresh は起動しない (スナップショット読み取りのみ)。
     * 本サービスは {@code @Transactional(readOnly=true)} で稼働しており、内部から HTTP refresh +
     * REQUIRES_NEW write tx を起動するのは設計違反。さらに 60 秒間隔の polling では token
     * 残時間 5 分以下になった瞬間から毎分 refresh と /accounts 呼び出しが発火し続ける。
     * <p>
     * 期限切れ間近 / 期限切れ token をそのまま使って 401 が返れば apiReachable=false 判定。
     * 画面側は別途 OAuth 状態 (有効期限) を表示しているため、ユーザーは再認可を判断できる。
     */
    private boolean pingMfApi() {
        try {
            MfOauthService.TokenSnapshot snap = mfOauthService.loadActiveTokenSnapshot();
            mfApiClient.listAccounts(snap.client(), snap.accessToken());
            return true;
        } catch (RuntimeException e) {
            log.warn("[mf-health] MF API ping 失敗: {}", e.getMessage());
            return false;
        }
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
