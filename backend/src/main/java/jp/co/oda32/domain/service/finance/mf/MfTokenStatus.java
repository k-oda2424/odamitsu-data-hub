package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * MF OAuth2 トークン状態（画面表示用）。
 * <p>
 * P1-01 (DD-F-04): {@code mfTenantId} / {@code mfTenantName} / {@code tenantBoundAt}
 * を追加。別会社 MF の誤接続検知のため、UI で「連携先: {tenantName} (id: {tenantId})」を
 * 表示する。tenantBoundAt が NULL なら旧データ互換 (初回 callback で binding 確定予定)。
 * <p>
 * P1-04 (案 α): {@code refreshTokenIssuedAt} / {@code daysUntilReauth} を追加。
 * MF refresh_token は 540 日寿命のため、現 active token の {@code add_date_time} を
 * 発行日として残日数を算出し、グローバル top header の予兆 banner で表示する。
 * {@code reAuthRequired} は「{@code daysUntilReauth} ≤ 0」のとき true (= 既に失効)。
 * <p>
 * T6 (2026-05-04): {@code missingScopes} / {@code extraScopes} / {@code scopeOk} を追加。
 * admin が画面で {@code scope} を編集して必須 scope を消した場合に、関連 API が
 * 403 で失敗するまで気付かない問題への対応。{@link MfScopeConstants#analyze(String)}
 * で解析し、{@code MfScopeBanner} で UI 警告を出す。
 * <p>
 * G1-M4 (2026-05-06): {@code reAuthExpired} を追加。{@code refresh_token_issued_at} 起点で
 * 540 日寿命を超過した場合 true。{@code reAuthRequired} は {@code remaining ≤ 0} で立つが、
 * {@code reAuthExpired} は **負値 (= 既に超過)** の状態のみを示す。UI banner で最上位 severity
 * (期限超過、destructive banner) を出すために独立フラグとして公開する。
 */
public record MfTokenStatus(
        boolean configured,         // Client ID/Secret が DB に登録済みか
        boolean connected,          // 有効な access_token があるか（期限切れ含む）
        Instant expiresAt,          // access_token 有効期限（未接続時 null）
        String scope,               // トークンの scope
        Instant lastRefreshedAt,    // 最後に token 更新した時刻
        @JsonProperty("reAuthRequired") boolean reAuthRequired,         // refresh_token が期限切れ (daysUntilReauth ≤ 0) で再認可必須
        String mfTenantId,          // バインド済 MF tenant id (P1-01)
        String mfTenantName,        // バインド済 MF tenant 名 (P1-01)
        Instant tenantBoundAt,      // tenant binding 確定時刻 (P1-01)
        @JsonProperty("refreshTokenIssuedAt") Instant refreshTokenIssuedAt, // refresh_token の真の発行日 (G1-M4)
        @JsonProperty("daysUntilReauth") Integer daysUntilReauth,           // 540 日 - 経過日数 (P1-04、未接続時 null、超過時は 0 にクランプ)
        List<String> missingScopes, // T6: 必須だが scope に含まれていないもの (空 list = OK)
        List<String> extraScopes,   // T6: scope に含まれているが必須ではないもの (空 list = OK、警告レベル)
        @JsonProperty("scopeOk") boolean scopeOk, // T6: missingScopes.isEmpty() の short-hand (UI 判定用)
        @JsonProperty("reAuthExpired") boolean reAuthExpired // G1-M4: 540 日超過 = 期限超過の明示フラグ (UI 最上位 severity)
) {}
