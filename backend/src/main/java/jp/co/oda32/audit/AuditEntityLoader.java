package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * T2 (C5): {@link AuditLog} の before/after snapshot 取得用ローダー。
 * <p>
 * 各 finance テーブルごとに {@code @Component} として実装し、{@link AuditEntityLoaderRegistry}
 * へ自動登録する (Spring の DI list 経由)。
 * <p>
 * Aspect は (1) メソッド呼び出し前に {@link #loadByPk(JsonNode)} を実行して before snapshot を取得し、
 * (2) メソッド呼び出し後に再度 {@link #loadByPk(JsonNode)} を実行して after snapshot を取得する。
 * これにより `before_values` / `after_values` には実 DB row が記録される (引数 JSON ではない)。
 *
 * @since 2026-05-04 (C5)
 */
public interface AuditEntityLoader {

    /**
     * 対象テーブル名。{@link AuditLog#table()} と一致する文字列を返す。
     */
    String table();

    /**
     * PK JSON から Entity を取得する。
     * <p>
     * PK JSON は {@link AuditLog#pkExpression()} の評価結果 (Map → JSONB 化) または
     * {@link AuditLog#pkArgIndex()} の引数値が JSONB 化されたもの。
     * Loader 側で必要なフィールドを取り出して Repository を呼ぶ。
     * <p>
     * 取得不能 (キー不足、Entity 未存在、INSERT 想定で before が無い等) は {@link Optional#empty()} を返す。
     * 例外を投げると Aspect が監査記録を諦めるだけで業務 tx には影響しない (warn ログ)。
     */
    Optional<Object> loadByPk(JsonNode pkJson);
}
