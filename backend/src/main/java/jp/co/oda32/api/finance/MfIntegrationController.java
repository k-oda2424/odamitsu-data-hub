package jp.co.oda32.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.mf.MfApiClient;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import org.springframework.core.env.Environment;
import jp.co.oda32.dto.finance.MfOauthClientRequest;
import jp.co.oda32.dto.finance.MfOauthClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    // ---- 診断用（勘定科目同期実装前のレスポンス shape 調査） ----
    // dev/test プロファイルのみ動作。prod では 404 を返す (B-W4)。

    @GetMapping("/debug/accounts-raw")
    public ResponseEntity<?> debugAccountsRaw() {
        if (!isDevProfile()) return notFound();
        return fetchRawWithFirstN("/api/v3/accounts", 3);
    }

    @GetMapping("/debug/taxes-raw")
    public ResponseEntity<?> debugTaxesRaw() {
        if (!isDevProfile()) return notFound();
        return fetchRawWithFirstN("/api/v3/taxes", 5);
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
