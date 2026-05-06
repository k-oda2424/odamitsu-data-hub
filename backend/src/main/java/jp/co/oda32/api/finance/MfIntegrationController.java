package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.service.finance.mf.MfAccountSyncService;
import jp.co.oda32.domain.service.finance.mf.MfBalanceReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfEnumTranslationService;
import jp.co.oda32.domain.service.finance.mf.MfJournalReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfTenantMismatchException;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.MfEnumTranslationRequest;
import jp.co.oda32.dto.finance.MfEnumTranslationResponse;
import jp.co.oda32.dto.finance.MfOauthClientRequest;
import jp.co.oda32.dto.finance.MfOauthClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * マネーフォワードクラウド会計 連携（Phase 1: OAuth 基盤 + 仕訳突合）。
 * <p>
 * 設計書: claudedocs/design-mf-integration-status.md
 *
 * <p>SF-12: ログインユーザ取得は {@link LoginUserUtil#getLoginUserInfo()} に統一。
 * <p>SF-13: 診断用 endpoint は {@link MfIntegrationDebugController} に分離。
 * <p>SF-15: 401/403/422 のレスポンス組み立ては {@link FinanceExceptionHandler} に集約。
 *
 * @since 2026/04/20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance/mf-integration")
@PreAuthorize("@loginUserSecurityBean.isAdmin()")
@RequiredArgsConstructor
public class MfIntegrationController {

    private final MfOauthService mfOauthService;
    private final MfEnumTranslationService enumTranslationService;
    private final MfAccountSyncService accountSyncService;
    private final MfJournalReconcileService reconcileService;
    private final MfBalanceReconcileService balanceReconcileService;

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
            @Valid @RequestBody MfOauthClientRequest request) throws Exception {
        MMfOauthClient entity = MMfOauthClient.builder()
                .clientId(request.getClientId())
                .redirectUri(request.getRedirectUri())
                .scope(request.getScope())
                .authorizeUrl(request.getAuthorizeUrl())
                .tokenUrl(request.getTokenUrl())
                .apiBaseUrl(request.getApiBaseUrl())
                .build();
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
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
    public ResponseEntity<?> authorizeUrl() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MfOauthService.AuthorizeUrl issued = mfOauthService.buildAuthorizeUrl(userNo);
        return ResponseEntity.ok(Map.of("url", issued.url(), "state", issued.state()));
    }

    /**
     * MF からのリダイレクト後、フロントの callback ページが code/state を POST してくる。
     * state + code_verifier (PKCE) が DB ストアで検証できれば token 交換を行う。
     *
     * <p>SF-04: token endpoint エラー時のメッセージは汎用化 (詳細は server log のみ)。
     * このため {@link MfReAuthRequiredException} だけは {@link FinanceExceptionHandler} に
     * 委譲せずローカルで処理する (専用文言が必要)。
     */
    @PostMapping("/oauth/callback")
    public ResponseEntity<?> callback(@RequestBody Map<String, String> body) throws Exception {
        String code = body.get("code");
        String state = body.get("state");
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "code/state が必要です"));
        }
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        try {
            mfOauthService.handleCallback(code, state, userNo);
            return ResponseEntity.ok(mfOauthService.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("MF OAuth callback: state 検証失敗 userNo={} message={}", userNo, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "state が不正または有効期限切れです。再度認可をやり直してください。"));
        } catch (MfReAuthRequiredException e) {
            // SF-04: ユーザー向けメッセージは汎用化、詳細 (token endpoint body) はサーバーログのみ
            log.warn("MF OAuth callback: 再認証が必要 userNo={}", userNo, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "再認証が必要です。詳細は管理者ログを参照してください"));
        } catch (MfTenantMismatchException e) {
            // P1-01: 別会社 MF を認可した場合、運用者に明示的なメッセージを返す。
            log.error("MF OAuth callback: tenant binding 不一致 userNo={} bound={} observed={}",
                    userNo, e.getBoundTenantId(), e.getObservedTenantId());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage(), "code", "MF_TENANT_MISMATCH"));
        }
    }

    /** 接続を切断（DB トークンを論理削除）。 */
    @PostMapping("/oauth/revoke")
    public ResponseEntity<MfTokenStatus> revoke() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
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
            @Valid @RequestBody List<MfEnumTranslationRequest> requests) throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
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
    public ResponseEntity<?> autoSeedTranslations() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MfEnumTranslationService.AutoSeedResult result = enumTranslationService.autoSeed(userNo);
        return ResponseEntity.ok(Map.of(
                "added", result.added(),
                "unresolved", result.unresolved()
        ));
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
        MfJournalReconcileService.ReconcileReport report = reconcileService.reconcile(transactionMonth);
        return ResponseEntity.ok(report);
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
        MfBalanceReconcileService.BalanceReconcileReport report =
                balanceReconcileService.reconcile(period);
        return ResponseEntity.ok(report);
    }

    // ---- 勘定科目同期 (mf_account_master 洗い替え) ----

    @GetMapping("/account-sync/preview")
    public ResponseEntity<?> previewAccountSync() {
        MfAccountSyncService.SyncResult result = accountSyncService.preview();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/account-sync/apply")
    public ResponseEntity<?> applyAccountSync() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MfAccountSyncService.SyncResult result = accountSyncService.apply(userNo);
        return ResponseEntity.ok(result);
    }
}
