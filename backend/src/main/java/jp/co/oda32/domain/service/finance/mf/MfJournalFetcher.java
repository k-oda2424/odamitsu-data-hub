package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.exception.FinanceBusinessException;
import jp.co.oda32.exception.FinanceInternalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * MF /api/v3/journals を期間で全件取得する共通 helper。
 * <p>
 * 以下の責務を集約:
 * <ul>
 *   <li>fiscal year 境界 (400 "Given date is not matching any accounting periods") の多段 fallback</li>
 *   <li>rate limit (429) 対策の指数バックオフ retry</li>
 *   <li>pagination 間の sleep (350ms)</li>
 *   <li>pagination 最大 50 ページの safeguard</li>
 *   <li>transactionDate → 20 日締め月への bucket key 変換</li>
 * </ul>
 * <p>
 * 利用元: {@link MfSupplierLedgerService}, AccountsPayableIntegrityService
 * <p>
 * 設計書: claudedocs/design-integrity-report.md §5.2 (R3 反映で切り出し)
 *
 * @since 2026-04-22
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class MfJournalFetcher {

    private static final int PER_PAGE = 1000;
    private static final int MAX_PAGES = 50;
    /** MF API のレート制限 (Operations per second) 回避のため、リクエスト間に挿入する遅延 (ms)。 */
    private static final long RATE_LIMIT_SLEEP_MS = 350;

    private final MfApiClient mfApiClient;

    /**
     * 期間内の MF /journals を全件取得する。
     * <p>
     * fiscal year 境界対策: start_date 候補を {@link #buildStartDateCandidates} で列挙し、
     * 400 "accounting periods" エラーを受けたら次の候補に fallback。
     * 全候補で失敗したら {@link IllegalStateException}。
     *
     * @param client       MF OAuth クライアント
     * @param accessToken  Bearer token
     * @param fromMonth    期間開始 (20 日締め月)
     * @param toMonth      期間終了 (20 日締め月)
     * @return 取得した journals と、実際に採用された start_date
     */
    public FetchResult fetchJournalsForPeriod(MMfOauthClient client, String accessToken,
                                         LocalDate fromMonth, LocalDate toMonth) {
        List<LocalDate> candidates = buildStartDateCandidates(fromMonth, toMonth);

        List<MfJournal> allJournals = null;
        LocalDate actualStart = null;
        Exception lastError = null;
        boolean firstAttempt = true;
        for (LocalDate candidate : candidates) {
            if (candidate.isAfter(toMonth)) continue;
            if (!firstAttempt) sleepQuietly(RATE_LIMIT_SLEEP_MS);
            firstAttempt = false;
            try {
                allJournals = fetchAllJournalsPaged(client, accessToken, candidate, toMonth);
                actualStart = candidate;
                break;
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 400
                        && e.getResponseBodyAsString() != null
                        && e.getResponseBodyAsString().contains("accounting periods")) {
                    log.info("[mf-journal-fetcher] MF accounting periods 範囲外: start_date={} → 次候補へ", candidate);
                    lastError = e;
                    continue;
                }
                throw e;
            }
        }
        if (allJournals == null) {
            // T5: ユーザーが MF 事業年度設定で対処可能な業務エラー。
            // last error の MF 詳細は機微情報を含まないので message に含める。
            throw new FinanceBusinessException(
                    "MF fiscal year 境界エラー: どの start_date 候補でも journals を取得できませんでした。"
                            + " 取引月の期間を見直すか、MF 事業年度設定を確認してください。"
                            + (lastError != null ? " 最終エラー: " + lastError.getMessage() : ""));
        }
        return new FetchResult(allJournals, actualStart);
    }

    /**
     * 1 日分の journals を fiscal year fallback なしで取得 (reconcile 用)。
     * <p>
     * MA-04: {@link #fetchJournalsForPeriod} は fiscal year 境界対応で start_date を
     * 最大 30 候補列挙し、最大約 1 ヶ月分を fetch する。reconcile では
     * {@code (transactionMonth, transactionMonth)} の 1 日分しか必要としないため、
     * 候補数 30 倍 / pagination 数 / RATE_LIMIT_SLEEP の総量がそのまま余剰負荷となる。
     * <p>
     * 本メソッドは start_date == end_date == date でダイレクトに 1 度だけ pagination 取得し、
     * 400 "accounting periods" の fallback も行わない。fiscal year 跨ぎが発生するのは
     * 月初のごく一部の日付に限られ、reconcile 利用者は呼び出し前に取引月 (20 日締め) を
     * 渡すため境界に当たることは稀。万一 400 が返れば呼び出し元に伝播させる。
     *
     * @since 2026-05-04 (MA-04)
     */
    public MfJournalsResponse fetchJournalsSingleDay(MMfOauthClient client, String accessToken,
                                                     LocalDate date) {
        List<MfJournal> all = fetchAllJournalsPaged(client, accessToken, date, date);
        return new MfJournalsResponse(all);
    }

    /**
     * fiscal year 境界で 400 になるケースを回避するための start_date 候補列挙。
     * fromMonth 〜 toMonth の各月の 1日/21日 を広範囲に試行 (最大 30 候補)。
     */
    static List<LocalDate> buildStartDateCandidates(LocalDate fromMonth, LocalDate toMonth) {
        LinkedHashSet<LocalDate> set = new LinkedHashSet<>();
        // 1) 前月 21日: 自社 bucket と整合する理想形 (前 fiscal year 内なら成功)
        set.add(fromMonth.minusMonths(1).plusDays(1));
        // 2) fromMonth 自身
        set.add(fromMonth);
        // 3) fromMonth + 1日
        set.add(fromMonth.plusDays(1));
        // 4) fromMonth から toMonth まで、各月の 1日 / 21日
        LocalDate cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            YearMonth ym = YearMonth.from(cursor);
            set.add(ym.atDay(1));
            set.add(ym.atDay(21));
            cursor = ym.plusMonths(1).atDay(1);
            if (set.size() > 30) break;
        }
        return new ArrayList<>(set);
    }

    /**
     * pagination 全件取得。各ページ間に 350ms sleep。
     * 429/5xx retry は {@link MfApiClient#listJournals} 側 (executeWithRetry) で実施 (SF-08)。
     */
    private List<MfJournal> fetchAllJournalsPaged(MMfOauthClient client, String accessToken,
                                                    LocalDate startDate, LocalDate endDate) {
        List<MfJournal> all = new ArrayList<>();
        String sd = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String ed = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        int page = 1;
        while (true) {
            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, sd, ed, page, PER_PAGE);
            List<MfJournal> items = res.items();
            all.addAll(items);
            if (items.size() < PER_PAGE) break;
            page++;
            if (page > MAX_PAGES) {
                // T5: 内部 safeguard (data set が想定範囲を超えた異常)。
                throw new FinanceInternalException("MF journals ページング safeguard を超過しました (50 pages)");
            }
            sleepQuietly(RATE_LIMIT_SLEEP_MS);
        }
        return all;
    }

    /**
     * transactionDate を 20日締め月 (LocalDate of 20日) に寄せる。
     * day <= 20 → 当月20日、day > 20 → 翌月20日。
     */
    public static LocalDate toClosingMonthDay20(LocalDate date) {
        if (date.getDayOfMonth() <= 20) {
            return YearMonth.from(date).atDay(20);
        }
        return YearMonth.from(date).plusMonths(1).atDay(20);
    }

    /**
     * 買掛金 期首残高仕訳 (opening balance journal) を判定する。
     * <p>SF-G08: 判定ロジックを {@link MfOpeningJournalDetector#isOpeningCandidate} に集約。
     * 後方互換のため本メソッドは残し、内部で detector に委譲する。
     */
    public static boolean isPayableOpeningJournal(MfJournal j) {
        return MfOpeningJournalDetector.isOpeningCandidate(j);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fetch 結果: 取得した journals と、実際に採用した start_date。 */
    public record FetchResult(List<MfJournal> journals, LocalDate actualStart) {}
}
