package jp.co.oda32.domain.service.finance.mf;

import java.util.List;
import java.util.Set;

/**
 * MF 連携で必須となる OAuth scope の typed list (T6)。
 * <p>
 * フロント {@code frontend/types/mf-integration.ts} の {@code MF_REQUIRED_SCOPES}
 * と同期させること。新たに必須 scope を追加する場合、両方を同時に更新し、
 * Javadoc / コメントの説明も合わせて修正する。
 *
 * <p>現状の必須 scope (Phase 1 仕訳突合 + tenant binding):
 * <ul>
 *   <li>{@code mfc/accounting/journal.read} - 仕訳取得 (突合用)</li>
 *   <li>{@code mfc/accounting/accounts.read} - 勘定科目取得</li>
 *   <li>{@code mfc/accounting/offices.read} - 事業所取得</li>
 *   <li>{@code mfc/accounting/taxes.read} - 税区分取得</li>
 *   <li>{@code mfc/accounting/report.read} - 試算表取得</li>
 *   <li>{@code mfc/admin/tenant.read} - tenant binding (P1-01)</li>
 * </ul>
 *
 * <p>scope 変更検知ロジック ({@link #analyze(String)}):
 * admin が {@code m_mf_oauth_client.scope} を編集して必須 scope を消した場合、
 * 関連 API が 403 で失敗するまで気付かない。本クラスを使って banner で予兆検知する。
 *
 * @since 2026-05-04 (T6 Scope 変更検知 + 警告 UI)
 */
public final class MfScopeConstants {

    public static final List<String> REQUIRED_SCOPES = List.of(
            "mfc/accounting/journal.read",
            "mfc/accounting/accounts.read",
            "mfc/accounting/offices.read",
            "mfc/accounting/taxes.read",
            "mfc/accounting/report.read",
            "mfc/admin/tenant.read"
    );

    private MfScopeConstants() {}

    /**
     * 与えられた scope 文字列 (DB 上の {@code scope} カラム値、空白区切り) を解析し、
     * 必須 scope に対する不足 / 追加を返す。
     * <p>
     * {@code missing} に要素があれば該当 API は 403 になるため再認可必須 (banner 赤表示)。
     * {@code extra} は警告レベル (動作には影響しない管理上の指標)。
     *
     * @param actualScopes DB 値 (例: {@code "mfc/accounting/journal.read mfc/accounting/accounts.read"})。
     *                     null / blank は scope 未設定として扱う (= 全 missing)。
     * @return ScopeAnalysis (missing / extra のリスト、いずれも immutable)
     */
    public static ScopeAnalysis analyze(String actualScopes) {
        Set<String> actual = (actualScopes == null || actualScopes.isBlank())
                ? Set.of()
                : Set.of(actualScopes.trim().split("\\s+"));
        List<String> missing = REQUIRED_SCOPES.stream()
                .filter(s -> !actual.contains(s))
                .toList();
        List<String> extra = actual.stream()
                .filter(s -> !REQUIRED_SCOPES.contains(s))
                .sorted()
                .toList();
        return new ScopeAnalysis(missing, extra);
    }

    /**
     * scope 解析結果。
     *
     * @param missing 必須だが現 scope に含まれていないもの (要素あり = 警告)
     * @param extra   現 scope に含まれているが必須ではないもの (要素あり = 情報)
     */
    public record ScopeAnalysis(List<String> missing, List<String> extra) {}
}
