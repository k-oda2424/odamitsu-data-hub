package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MF /journals の月単位累積キャッシュ。
 * <p>
 * 設計方針: MF API 通信を最小化する。
 * <ul>
 *   <li>キャッシュ粒度: shopNo × 20日締め月</li>
 *   <li>TTL なし (一度取得したらサーバー再起動まで保持)</li>
 *   <li>差分取得: 要求期間のうち未取得月だけを 1 回の API コールで fetch</li>
 *   <li>再取得: refresh=true で対象期間をキャッシュから discard → 再 fetch</li>
 *   <li>全端末共有 (サーバーメモリ)</li>
 * </ul>
 * <p>
 * 利用元: {@link MfSupplierLedgerService} (/ledger/mf),
 *         AccountsPayableIntegrityService (/integrity-report)
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfJournalCacheService {

    private final MfJournalFetcher fetcher;

    /** shopNo → monthly cache */
    private final Map<Integer, MonthlyCache> cachesByShop = new ConcurrentHashMap<>();

    /**
     * 要求期間の journals を取得する。キャッシュ済みの月はキャッシュから、未取得月は MF API から差分取得。
     *
     * @param shopNo      ショップ番号 (キャッシュ分離キー)
     * @param client      MF OAuth client
     * @param accessToken Bearer token
     * @param fromMonth   期間開始 (20日締め月)
     * @param toMonth     期間終了 (20日締め月)
     * @param refresh     true なら要求期間のキャッシュを破棄して全件再取得
     * @return 要求期間の journals と、期間内の最古 fetchedAt
     */
    public synchronized CachedResult getOrFetch(Integer shopNo, MMfOauthClient client, String accessToken,
                                                LocalDate fromMonth, LocalDate toMonth, boolean refresh) {
        MonthlyCache cache = cachesByShop.computeIfAbsent(shopNo, k -> new MonthlyCache());

        if (refresh) {
            invalidatePeriod(cache, fromMonth, toMonth);
            log.info("[mf-cache] shopNo={} refresh=true: {}〜{} のキャッシュを破棄", shopNo, fromMonth, toMonth);
        }

        List<LocalDate> missing = collectMissingMonths(cache, fromMonth, toMonth);
        if (!missing.isEmpty()) {
            LocalDate fetchFrom = missing.get(0);
            LocalDate fetchTo = missing.get(missing.size() - 1);
            log.info("[mf-cache] shopNo={} cache miss: {}〜{} を fetch ({} 月分)",
                    shopNo, fetchFrom, fetchTo, missing.size());
            MfJournalFetcher.FetchResult fetched = fetcher.fetchJournalsForPeriod(
                    client, accessToken, fetchFrom, fetchTo);
            storeFetched(cache, missing, fetched.journals());
        } else {
            log.info("[mf-cache] shopNo={} cache hit: {}〜{} (MF API 通信なし)", shopNo, fromMonth, toMonth);
        }

        return combine(cache, fromMonth, toMonth);
    }

    /** 特定 shop の全キャッシュを破棄。 */
    public void invalidateAll(Integer shopNo) {
        cachesByShop.remove(shopNo);
        log.info("[mf-cache] shopNo={} 全キャッシュ破棄", shopNo);
    }

    private void invalidatePeriod(MonthlyCache cache, LocalDate fromMonth, LocalDate toMonth) {
        LocalDate cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            cache.journalsByMonth.remove(cursor);
            cache.fetchedMonths.remove(cursor);
            cache.fetchedAtByMonth.remove(cursor);
            cursor = nextMonth(cursor);
        }
    }

    private List<LocalDate> collectMissingMonths(MonthlyCache cache, LocalDate fromMonth, LocalDate toMonth) {
        List<LocalDate> missing = new ArrayList<>();
        LocalDate cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            if (!cache.fetchedMonths.contains(cursor)) missing.add(cursor);
            cursor = nextMonth(cursor);
        }
        return missing;
    }

    private void storeFetched(MonthlyCache cache, List<LocalDate> missing, List<MfJournal> fetchedJournals) {
        Map<LocalDate, List<MfJournal>> byMonth = new HashMap<>();
        for (MfJournal j : fetchedJournals) {
            if (j.transactionDate() == null) continue;
            LocalDate month = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(j);
        }
        Instant now = Instant.now();
        for (LocalDate month : missing) {
            cache.journalsByMonth.put(month, byMonth.getOrDefault(month, List.of()));
            cache.fetchedMonths.add(month);
            cache.fetchedAtByMonth.put(month, now);
        }
    }

    private CachedResult combine(MonthlyCache cache, LocalDate fromMonth, LocalDate toMonth) {
        List<MfJournal> result = new ArrayList<>();
        Instant oldest = null;
        LocalDate cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            List<MfJournal> mj = cache.journalsByMonth.get(cursor);
            if (mj != null) result.addAll(mj);
            Instant f = cache.fetchedAtByMonth.get(cursor);
            if (f != null && (oldest == null || f.isBefore(oldest))) oldest = f;
            cursor = nextMonth(cursor);
        }
        return new CachedResult(result, oldest);
    }

    private static LocalDate nextMonth(LocalDate day20) {
        return YearMonth.from(day20).plusMonths(1).atDay(20);
    }

    /** shop 単位の月次キャッシュ本体。 */
    private static class MonthlyCache {
        final Map<LocalDate, List<MfJournal>> journalsByMonth = new ConcurrentHashMap<>();
        final Set<LocalDate> fetchedMonths = ConcurrentHashMap.newKeySet();
        final Map<LocalDate, Instant> fetchedAtByMonth = new ConcurrentHashMap<>();
    }

    /** キャッシュ or fetch 結果。 */
    public record CachedResult(List<MfJournal> journals, Instant oldestFetchedAt) {}
}
