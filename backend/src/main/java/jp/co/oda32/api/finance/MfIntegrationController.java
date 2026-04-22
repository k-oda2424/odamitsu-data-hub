package jp.co.oda32.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.mf.MfAccountSyncService;
import jp.co.oda32.domain.service.finance.mf.MfApiClient;
import jp.co.oda32.domain.service.finance.mf.MfEnumTranslationService;
import jp.co.oda32.domain.service.finance.mf.MfBalanceReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfJournalReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import org.springframework.core.env.Environment;
import jp.co.oda32.dto.finance.MfEnumTranslationRequest;
import jp.co.oda32.dto.finance.MfEnumTranslationResponse;
import jp.co.oda32.dto.finance.MfOauthClientRequest;
import jp.co.oda32.dto.finance.MfOauthClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * マネーフォワードクラウド会計 連携（Phase 1: OAuth 基盤 + 仕訳突合）。
 * <p>
 * 設計書: claudedocs/design-mf-integration-status.md
 *
 * @since 2026/04/20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance/mf-integration")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MfIntegrationController {

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final Environment environment;
    private final MfEnumTranslationService enumTranslationService;
    private final MfAccountSyncService accountSyncService;
    private final MfJournalReconcileService reconcileService;
    private final MfBalanceReconcileService balanceReconcileService;
    private final jp.co.oda32.domain.repository.smile.TSmilePaymentRepository tSmilePaymentRepository;

    // ---- クライアント設定 (admin 登録用) ----

    @GetMapping("/oauth/client")
    public ResponseEntity<MfOauthClientResponse> getClient() {
        return mfOauthService.findActiveClient()
                .map(MfOauthClientResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(MfOauthClientResponse.builder().build()));
    }

    @PutMapping("/oauth/client")
    public ResponseEntity<MfOauthClientResponse> upsertClient(
            @Valid @RequestBody MfOauthClientRequest request,
            @AuthenticationPrincipal LoginUser user) {
        MMfOauthClient entity = MMfOauthClient.builder()
                .clientId(request.getClientId())
                .redirectUri(request.getRedirectUri())
                .scope(request.getScope())
                .authorizeUrl(request.getAuthorizeUrl())
                .tokenUrl(request.getTokenUrl())
                .apiBaseUrl(request.getApiBaseUrl())
                .build();
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        MMfOauthClient saved = mfOauthService.upsertClient(entity, request.getClientSecret(), userNo);
        return ResponseEntity.ok(MfOauthClientResponse.from(saved));
    }

    // ---- OAuth フロー ----

    /** 接続ステータスを返す（画面トップの表示用）。 */
    @GetMapping("/oauth/status")
    public ResponseEntity<MfTokenStatus> status() {
        return ResponseEntity.ok(mfOauthService.getStatus());
    }

    /**
     * 認可 URL を組み立てて返す。state + PKCE code_verifier は DB ストア (B-3/B-4) で TTL 管理。
     * フロントは受け取った URL を新タブで開く。
     */
    @GetMapping("/oauth/authorize-url")
    public ResponseEntity<?> authorizeUrl(@AuthenticationPrincipal LoginUser user) {
        try {
            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
            MfOauthService.AuthorizeUrl issued = mfOauthService.buildAuthorizeUrl(userNo);
            return ResponseEntity.ok(Map.of("url", issued.url(), "state", issued.state()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * MF からのリダイレクト後、フロントの callback ページが code/state を POST してくる。
     * state + code_verifier (PKCE) が DB ストアで検証できれば token 交換を行う。
     */
    @PostMapping("/oauth/callback")
    public ResponseEntity<?> callback(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal LoginUser user) {
        String code = body.get("code");
        String state = body.get("state");
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "code/state が必要です"));
        }
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        try {
            mfOauthService.handleCallback(code, state, userNo);
            return ResponseEntity.ok(mfOauthService.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("MF OAuth callback: state 検証失敗 userNo={} message={}", userNo, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "state が不正または有効期限切れです。再度認可をやり直してください。"));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /** 接続を切断（DB トークンを論理削除）。 */
    @PostMapping("/oauth/revoke")
    public ResponseEntity<MfTokenStatus> revoke(@AuthenticationPrincipal LoginUser user) {
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        mfOauthService.revoke(userNo);
        return ResponseEntity.ok(mfOauthService.getStatus());
    }

    // ---- enum 翻訳辞書 ----

    @GetMapping("/enum-translations")
    public ResponseEntity<List<MfEnumTranslationResponse>> listTranslations() {
        List<MfEnumTranslationResponse> list = enumTranslationService.findAll().stream()
                .map(MfEnumTranslationResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    /** 渡されたリストで翻訳辞書を洗い替え。 */
    @PutMapping("/enum-translations")
    public ResponseEntity<List<MfEnumTranslationResponse>> replaceTranslations(
            @Valid @RequestBody List<MfEnumTranslationRequest> requests,
            @AuthenticationPrincipal LoginUser user) {
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        List<MMfEnumTranslation> entities = requests.stream()
                .map(r -> MMfEnumTranslation.builder()
                        .enumKind(r.getEnumKind())
                        .englishCode(r.getEnglishCode())
                        .japaneseName(r.getJapaneseName())
                        .build())
                .toList();
        enumTranslationService.upsertAll(entities, userNo);
        return listTranslations();
    }

    /** MF API /accounts と既存 mf_account_master を突合して、英→日マッピングを自動学習。 */
    @PostMapping("/enum-translations/auto-seed")
    public ResponseEntity<?> autoSeedTranslations(@AuthenticationPrincipal LoginUser user) {
        try {
            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
            MfEnumTranslationService.AutoSeedResult result = enumTranslationService.autoSeed(userNo);
            return ResponseEntity.ok(Map.of(
                    "added", result.added(),
                    "unresolved", result.unresolved()
            ));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 仕訳突合 ----

    /**
     * 指定取引月について、MF /journals と自社 CSV 出力元データを突合する。
     * transactionMonth は yyyy-MM-dd（通常は締め日）。
     */
    @GetMapping("/reconcile")
    public ResponseEntity<?> reconcile(
            @RequestParam("transactionMonth")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate transactionMonth) {
        try {
            MfJournalReconcileService.ReconcileReport report = reconcileService.reconcile(transactionMonth);
            return ResponseEntity.ok(report);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 指定月末時点の MF 試算表 buying 残高と自社 累積残の突合 (Phase B 最小版)。
     * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5
     */
    @GetMapping("/balance-reconcile")
    public ResponseEntity<?> balanceReconcile(
            @RequestParam("period")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate period) {
        try {
            MfBalanceReconcileService.BalanceReconcileReport report =
                    balanceReconcileService.reconcile(period);
            return ResponseEntity.ok(report);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (MfScopeInsufficientException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", e.getMessage(),
                    "requiredScope", e.getRequiredScope()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 勘定科目同期 (mf_account_master 洗い替え) ----

    @GetMapping("/account-sync/preview")
    public ResponseEntity<?> previewAccountSync() {
        try {
            MfAccountSyncService.SyncResult result = accountSyncService.preview();
            return ResponseEntity.ok(result);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/account-sync/apply")
    public ResponseEntity<?> applyAccountSync(@AuthenticationPrincipal LoginUser user) {
        try {
            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
            MfAccountSyncService.SyncResult result = accountSyncService.apply(userNo);
            return ResponseEntity.ok(result);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 診断用（勘定科目同期実装前のレスポンス shape 調査） ----
    // dev/test プロファイルのみ動作。prod では 404 を返す (B-W4)。

    @GetMapping("/debug/accounts-raw")
    public ResponseEntity<?> debugAccountsRaw() {
        if (!isDevProfile()) return notFound();
        return fetchRawWithFirstN("/api/v3/accounts", 3);
    }

    /**
     * t_smile_payment を voucher_date 範囲で集計する診断 endpoint (Phase B'' 遡及充填の前準備)。
     * 月×仕入先コード単位で payment_amount を合計して返す。
     * dev プロファイルのみ。
     */
    @GetMapping("/debug/smile-payment-monthly")
    public ResponseEntity<?> debugSmilePaymentMonthly(
            @RequestParam("fromDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fromDate,
            @RequestParam("toDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate toDate) {
        if (!isDevProfile()) return notFound();
        List<TSmilePayment> list = tSmilePaymentRepository.findByVoucherDateBetween(fromDate, toDate);
        // 月ごとに集計 (yyyy-MM) + supplier_code 単位
        java.util.Map<String, java.math.BigDecimal> byMonthTotal = new java.util.TreeMap<>();
        java.util.Map<String, Integer> byMonthCount = new java.util.TreeMap<>();
        java.util.Map<String, java.util.Map<String, java.math.BigDecimal>> byMonthSupplier = new java.util.TreeMap<>();
        for (TSmilePayment p : list) {
            if (p.getVoucherDate() == null) continue;
            String ym = p.getVoucherDate().toString().substring(0, 7);
            byMonthTotal.merge(ym,
                    p.getPaymentAmount() != null ? p.getPaymentAmount() : java.math.BigDecimal.ZERO,
                    java.math.BigDecimal::add);
            byMonthCount.merge(ym, 1, Integer::sum);
            byMonthSupplier.computeIfAbsent(ym, k -> new java.util.HashMap<>())
                    .merge(p.getSupplierCode() != null ? p.getSupplierCode() : "",
                            p.getPaymentAmount() != null ? p.getPaymentAmount() : java.math.BigDecimal.ZERO,
                            java.math.BigDecimal::add);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalRows", list.size());
        result.put("byMonth", byMonthTotal.entrySet().stream()
                .map(e -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
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
    @GetMapping("/debug/trial-balance-raw")
    public ResponseEntity<?> debugTrialBalanceRaw(@RequestParam Map<String, String> params) {
        if (!isDevProfile()) return notFound();
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(
                        e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        String path = "/api/v3/reports/trial_balance_bs" + (query.isEmpty() ? "" : "?" + query);
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(raw);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "message", "scope 不足です。frontend/types/mf-integration.ts に"
                                + " mfc/accounting/report.read を追加済みの状態で、"
                                + "/finance/mf-integration 画面から「再認証」してください。",
                        "mfBody", e.getResponseBodyAsString()));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", "MF API error", "mfBody", e.getResponseBodyAsString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/debug/taxes-raw")
    public ResponseEntity<?> debugTaxesRaw() {
        if (!isDevProfile()) return notFound();
        return fetchRawWithFirstN("/api/v3/taxes", 5);
    }

    /**
     * 指定取引月の MF 仕訳を最初 3 件だけ返す（shape 調査用）。
     * 例: /debug/journals-raw?transactionMonth=2026-03-20
     */
    @GetMapping("/debug/journals-raw")
    public ResponseEntity<?> debugJournalsRaw(
            @RequestParam("transactionMonth") String transactionMonth) {
        if (!isDevProfile()) return notFound();
        // start_date=end_date=transactionMonth で 1 日分のみ取得
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
    @GetMapping("/debug/account-journals")
    public ResponseEntity<?> debugAccountJournals(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        if (!isDevProfile()) return notFound();
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();

            java.util.List<java.util.Map<String, Object>> balancedJournals = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> unbalancedJournals = new java.util.ArrayList<>();

            int page = 1;
            final int perPage = 1000;
            while (true) {
                var res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
                var journals = res.items();
                if (journals.isEmpty()) break;
                for (var j : journals) {
                    if (j.branches() == null) continue;
                    java.math.BigDecimal deb = java.math.BigDecimal.ZERO;
                    java.math.BigDecimal cre = java.math.BigDecimal.ZERO;
                    java.util.List<java.util.Map<String, Object>> hitBranches = new java.util.ArrayList<>();
                    for (var b : j.branches()) {
                        var d = b.debitor();
                        var c = b.creditor();
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
                            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
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
                    java.math.BigDecimal diff = cre.subtract(deb);
                    java.util.Map<String, Object> jEntry = new java.util.LinkedHashMap<>();
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

            return ResponseEntity.ok(java.util.Map.of(
                    "accountName", accountName,
                    "startDate", startDate,
                    "endDate", endDate,
                    "balancedJournalCount", balancedJournals.size(),
                    "unbalancedJournalCount", unbalancedJournals.size(),
                    "unbalancedJournals", unbalancedJournals,
                    "balancedSamples", balancedJournals.stream().limit(3).toList()
            ));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 指定勘定科目の残高推移を計算する（/journals 全件走査）。
     * 例: /debug/account-trend?accountName=仕入資金複合&startDate=2025-10-01&endDate=2026-02-20
     */
    @GetMapping("/debug/account-trend")
    public ResponseEntity<?> debugAccountTrend(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        if (!isDevProfile()) return notFound();
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();

            java.math.BigDecimal totalDebit = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalCredit = java.math.BigDecimal.ZERO;
            java.util.TreeMap<java.time.LocalDate, java.math.BigDecimal[]> daily = new java.util.TreeMap<>();
            int hitCount = 0;

            int page = 1;
            final int perPage = 1000;
            while (true) {
                jp.co.oda32.domain.service.finance.mf.MfJournalsResponse res =
                        mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
                java.util.List<jp.co.oda32.domain.service.finance.mf.MfJournal> journals = res.items();
                if (journals.isEmpty()) break;
                for (jp.co.oda32.domain.service.finance.mf.MfJournal j : journals) {
                    if (j.branches() == null) continue;
                    for (jp.co.oda32.domain.service.finance.mf.MfJournal.MfBranch b : j.branches()) {
                        var deb = b.debitor();
                        var cre = b.creditor();
                        boolean hit = false;
                        java.math.BigDecimal dv = java.math.BigDecimal.ZERO;
                        java.math.BigDecimal cv = java.math.BigDecimal.ZERO;
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
                            java.time.LocalDate d = j.transactionDate();
                            if (d != null) {
                                java.math.BigDecimal[] arr = daily.computeIfAbsent(d,
                                        k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
                                arr[0] = arr[0].add(dv);
                                arr[1] = arr[1].add(cv);
                            }
                        }
                    }
                }
                if (journals.size() < perPage) break;
                page++;
            }

            // 累積残高推移（負債側として credit - debit 方式で計算）
            java.util.List<java.util.Map<String, Object>> trend = new java.util.ArrayList<>();
            java.math.BigDecimal running = java.math.BigDecimal.ZERO;
            for (var e : daily.entrySet()) {
                java.math.BigDecimal d = e.getValue()[0];
                java.math.BigDecimal c = e.getValue()[1];
                running = running.add(c).subtract(d);
                trend.add(java.util.Map.of(
                        "date", e.getKey().toString(),
                        "debit", d,
                        "credit", c,
                        "runningBalance", running
                ));
            }

            return ResponseEntity.ok(java.util.Map.of(
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
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private boolean isDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equals(p) || "test".equals(p)) return true;
        }
        return false;
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found"));
    }

    private ResponseEntity<?> fetchRawWithFirstN(String path, int sampleSize) {
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(trimToSample(raw, sampleSize));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
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
