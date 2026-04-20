package jp.co.oda32.domain.service.finance.mf;

import java.time.Instant;

/**
 * MF OAuth2 トークン状態（画面表示用）。
 */
public record MfTokenStatus(
        boolean configured,         // Client ID/Secret が DB に登録済みか
        boolean connected,          // 有効な access_token があるか（期限切れ含む）
        Instant expiresAt,          // access_token 有効期限（未接続時 null）
        String scope,               // トークンの scope
        Instant lastRefreshedAt,    // 最後に token 更新した時刻
        boolean reAuthRequired      // refresh_token が期限切れ等で再認可が必要
) {}
