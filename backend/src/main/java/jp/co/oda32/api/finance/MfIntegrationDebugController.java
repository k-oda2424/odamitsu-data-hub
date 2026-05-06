package jp.co.oda32.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.repository.smile.TSmilePaymentRepository;
import jp.co.oda32.domain.service.finance.mf.MfApiClient;
import jp.co.oda32.domain.service.finance.mf.MfDebugApiClient;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournalsResponse;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SF-13: MF 連携の診断用 endpoint 群 (dev/test プロファイル限定)。
 * <p>
 * 旧 {@link MfIntegrationController} の {@code /debug/*} 群を移動。
 * {@code @Profile} で dev/test のみ Bean 化されるため prod では Spring が認識せず 404 になる
 * (旧実装の各 endpoint 内 {@code isDevProfile()} ガードは不要に)。
 *
 * @since 2026-05-04 (SF-13)
 */
@Slf4j
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/finance/mf-integration/debug")
@PreAuthorize("@loginUserSecurityBean.isAdmin()")
@RequiredArgsConstructor
public class MfIntegrationDebugController {

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MfDebugApiClient mfDebugApiClient;
    private final TSmilePaymentRepository tSmilePaymentRepository;

    @GetMapping("/accounts-raw")
    public ResponseEntity<?> debugAccountsRaw() {
        return fetchRawWithFirstN("/api/v3/accounts", 3);
    }

    /**
     * t_smile_payment を voucher_date 範囲で集計する診断 endpoint (Phase B'' 遡及充填の前準備)。
     * 月×仕入先コード単位で payment_amount を合計して返す。
     */
    @GetMapping("/smile-payment-monthly")
    public ResponseEntity<?> debugSmilePaymentMonthly(
            @RequestParam("fromDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam("toDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        List<TSmilePayment> list = tSmilePaymentRepository.findByVoucherDateBetween(fromDate, toDate);
        Map<String, BigDecimal> byMonthTotal = new TreeMap<>();
        Map<String, Integer> byMonthCount = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> byMonthSupplier = new TreeMap<>();
        for (TSmilePayment p : list) {
            if (p.getVoucherDate() == null) continue;
            String ym = p.getVoucherDate().toString().substring(0, 7);
            byMonthTotal.merge(ym,
                    p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
            byMonthCount.merge(ym, 1, Integer::sum);
            byMonthSupplier.computeIfAbsent(ym, k -> new HashMap<>())
                    .merge(p.getSupplierCode() != null ? p.getSupplierCode() : "",
                            p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO,
                            BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", list.size());
        result.put("byMonth", byMonthTotal.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("month", e.getKey());
                    m.put("count", byMonthCount.get(e.getKey()));
                    m.put("totalAmount", e.getValue());
                    m.put("supplierCount", byMonthSupplier.get(e.getKey()).size());
                    return m;
                }).toList());
        return ResponseEntity.ok(result);
    }

    /**
     * 貸借対照表 試算表 API の生レスポンスを返す（Phase 0 スパイク用）。
     * 関連設計書: claudedocs/design-supplier-partner-ledger-balance.md §3.0
     *
     * <p>MF 側の必須 query は仕様未確定のため任意 param を透過で渡す。
     * 代表的な呼び方:
     * <pre>
     *   GET /debug/trial-balance-raw?from=2026-02-21&to=2026-03-20
     *   GET /debug/trial-balance-raw?period=2026-03-20
     * </pre>
     * 403 は scope 不足 (mfc/accounting/report.read 未取得) を意味するため
     * UI 再認可を促すメッセージで返す。
     */
    @GetMapping("/trial-balance-raw")
    public ResponseEntity<?> debugTrialBalanceRaw(@RequestParam Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(
                        e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        String path = "/api/v3/reports/trial_balance_bs" + (query.isEmpty() ? "" : "?" + query);
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfDebugApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(raw);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "message", "scope 不足です。frontend/types/mf-integration.ts に"
                                + " mfc/accounting/report.read を追加済みの状態で、"
                                + "/finance/mf-integration 画面から「再認証」してください。"));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", "MF API error"));
        }
    }

    @GetMapping("/taxes-raw")
    public ResponseEntity<?> debugTaxesRaw() {
        return fetchRawWithFirstN("/api/v3/taxes", 5);
    }

    /**
     * 指定取引月の MF 仕訳を最初 3 件だけ返す（shape 調査用）。
     * 例: /debug/journals-raw?transactionMonth=2026-03-20
     */
    @GetMapping("/journals-raw")
    public ResponseEntity<?> debugJournalsRaw(
            @RequestParam("transactionMonth") String transactionMonth) {
        String path = "/api/v3/journals?start_date=" + transactionMonth
                + "&end_date=" + transactionMonth
                + "&per_page=3";
        return fetchRawWithFirstN(path, 3);
    }

    /**
     * 指定勘定科目の全 branch を期間内で列挙し、journal 単位で credit/debit の釣り合いを判定する。
     * 不一致 journal が「ペアになっていない仕訳」を含む journal。
     * 例: /debug/account-journals?accountName=仕入資金複合&startDate=2026-02-21&endDate=2026-03-20
     */
    @GetMapping("/account-journals")
    public ResponseEntity<?> debugAccountJournals(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        List<Map<String, Object>> balancedJournals = new ArrayList<>();
        List<Map<String, Object>> unbalancedJournals = new ArrayList<>();

        int page = 1;
        final int perPage = 1000;
        while (true) {
            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
            List<MfJournal> journals = res.items();
            if (journals.isEmpty()) break;
            for (MfJournal j : journals) {
                if (j.branches() == null) continue;
                BigDecimal deb = BigDecimal.ZERO;
                BigDecimal cre = BigDecimal.ZERO;
                List<Map<String, Object>> hitBranches = new ArrayList<>();
                for (MfJournal.MfBranch b : j.branches()) {
                    MfJournal.MfSide d = b.debitor();
                    MfJournal.MfSide c = b.creditor();
                    boolean hit = false;
                    if (d != null && accountName.equals(d.accountName())) {
                        if (d.value() != null) deb = deb.add(d.value());
                        hit = true;
                    }
                    if (c != null && accountName.equals(c.accountName())) {
                        if (c.value() != null) cre = cre.add(c.value());
                        hit = true;
                    }
                    if (hit) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("debitAccount", d != null ? d.accountName() : null);
                        row.put("debitSub", d != null ? d.subAccountName() : null);
                        row.put("debitValue", d != null ? d.value() : null);
                        row.put("creditAccount", c != null ? c.accountName() : null);
                        row.put("creditSub", c != null ? c.subAccountName() : null);
                        row.put("creditValue", c != null ? c.value() : null);
                        row.put("remark", b.remark());
                        hitBranches.add(row);
                    }
                }
                if (hitBranches.isEmpty()) continue;
                BigDecimal diff = cre.subtract(deb);
                Map<String, Object> jEntry = new LinkedHashMap<>();
                jEntry.put("journalNumber", j.number());
                jEntry.put("transactionDate", j.transactionDate() != null ? j.transactionDate().toString() : null);
                jEntry.put("memo", j.memo());
                jEntry.put("debitSum", deb);
                jEntry.put("creditSum", cre);
                jEntry.put("diff", diff);
                jEntry.put("branches", hitBranches);
                if (diff.signum() == 0) balancedJournals.add(jEntry);
                else unbalancedJournals.add(jEntry);
            }
            if (journals.size() < perPage) break;
            page++;
        }

        return ResponseEntity.ok(Map.of(
                "accountName", accountName,
                "startDate", startDate,
                "endDate", endDate,
                "balancedJournalCount", balancedJournals.size(),
                "unbalancedJournalCount", unbalancedJournals.size(),
                "unbalancedJournals", unbalancedJournals,
                "balancedSamples", balancedJournals.stream().limit(3).toList()
        ));
    }

    /**
     * 指定勘定科目の残高推移を計算する（/journals 全件走査）。
     * 例: /debug/account-trend?accountName=仕入資金複合&startDate=2025-10-01&endDate=2026-02-20
     */
    @GetMapping("/account-trend")
    public ResponseEntity<?> debugAccountTrend(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        TreeMap<LocalDate, BigDecimal[]> daily = new TreeMap<>();
        int hitCount = 0;

        int page = 1;
        final int perPage = 1000;
        while (true) {
            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
            List<MfJournal> journals = res.items();
            if (journals.isEmpty()) break;
            for (MfJournal j : journals) {
                if (j.branches() == null) continue;
                for (MfJournal.MfBranch b : j.branches()) {
                    MfJournal.MfSide deb = b.debitor();
                    MfJournal.MfSide cre = b.creditor();
                    boolean hit = false;
                    BigDecimal dv = BigDecimal.ZERO;
                    BigDecimal cv = BigDecimal.ZERO;
                    if (deb != null && accountName.equals(deb.accountName())) {
                        if (deb.value() != null) {
                            dv = deb.value();
                            totalDebit = totalDebit.add(dv);
                        }
                        hit = true;
                    }
                    if (cre != null && accountName.equals(cre.accountName())) {
                        if (cre.value() != null) {
                            cv = cre.value();
                            totalCredit = totalCredit.add(cv);
                        }
                        hit = true;
                    }
                    if (hit) {
                        hitCount++;
                        LocalDate d = j.transactionDate();
                        if (d != null) {
                            BigDecimal[] arr = daily.computeIfAbsent(d,
                                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                            arr[0] = arr[0].add(dv);
                            arr[1] = arr[1].add(cv);
                        }
                    }
                }
            }
            if (journals.size() < perPage) break;
            page++;
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal[]> e : daily.entrySet()) {
            BigDecimal d = e.getValue()[0];
            BigDecimal c = e.getValue()[1];
            running = running.add(c).subtract(d);
            trend.add(Map.of(
                    "date", e.getKey().toString(),
                    "debit", d,
                    "credit", c,
                    "runningBalance", running
            ));
        }

        return ResponseEntity.ok(Map.of(
                "accountName", accountName,
                "startDate", startDate,
                "endDate", endDate,
                "hitBranchCount", hitCount,
                "totalDebit", totalDebit,
                "totalCredit", totalCredit,
                "balanceAsLiability", totalCredit.subtract(totalDebit),
                "balanceAsAsset", totalDebit.subtract(totalCredit),
                "trend", trend
        ));
    }

    private ResponseEntity<?> fetchRawWithFirstN(String path, int sampleSize) {
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfDebugApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(trimToSample(raw, sampleSize));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * レスポンスが配列 or {data:[...]} / {accounts:[...]} 等のいずれでも最初 N 件だけ残す。
     * shape 調査が目的なので wrapper と中身の両方が見えるように。
     */
    private JsonNode trimToSample(JsonNode raw, int n) {
        if (raw == null) return null;
        if (raw.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arr =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < Math.min(n, raw.size()); i++) arr.add(raw.get(i));
            return arr;
        }
        if (raw.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = raw.deepCopy();
            raw.fieldNames().forEachRemaining(field -> {
                JsonNode v = copy.get(field);
                if (v != null && v.isArray()) {
                    com.fasterxml.jackson.databind.node.ArrayNode arr =
                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
                    for (int i = 0; i < Math.min(n, v.size()); i++) arr.add(v.get(i));
                    copy.set(field, arr);
                }
            });
            return copy;
        }
        return raw;
    }
}
