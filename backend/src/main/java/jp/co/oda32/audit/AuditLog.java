package jp.co.oda32.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * T2: finance Service 層メソッドの監査ログ記録マーカー。
 * <p>
 * 付与すると {@link FinanceAuditAspect} が呼び出し前後の Entity スナップショットを
 * {@code finance_audit_log} に JSONB で書き込む。
 * <p>
 * <b>制約</b>:
 * <ul>
 *   <li>Service の public メソッドにのみ付与する (Spring AOP proxy 経由のみ AOP が起動するため)</li>
 *   <li>同一クラス内 self 呼び出し (this.method()) では起動しない。Controller / 別 Service 経由必須</li>
 *   <li>before snapshot を実 Entity から取得するためには {@link AuditEntityLoader} 実装を提供する</li>
 * </ul>
 *
 * <h2>PK の指定方法</h2>
 * <ol>
 *   <li>{@link #pkExpression()} (推奨、複合 PK 対応)
 *     <pre>
 *     pkExpression = "{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}"
 *     </pre>
 *     SpEL で評価され、{@code Map<String, Object>} を JSONB 化する。
 *   </li>
 *   <li>{@link #pkArgIndex()} (単一引数 PK の旧 API、後方互換)
 *     - {@code 0..n}: その index の引数を JSONB 化
 *     - {@code -1} (未指定): pkExpression を見る、それも未指定なら {@code {}}
 *   </li>
 * </ol>
 *
 * <h2>Before snapshot の取得</h2>
 * 対応する {@link AuditEntityLoader} (table 名で {@link AuditEntityLoaderRegistry} に登録) があれば、
 * Aspect が PK JSON を loader に渡して実 Entity を fetch し {@code before_values} に記録する。
 * Loader が無い table は {@code before_values=null} (引数 JSON は使わない、誤誘導回避)。
 *
 * @since 2026-05-04 (T2) — 2026-05-04 (C4/C5) で複合 PK + 実 before snapshot に拡張
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 監査対象テーブル名 (例: "t_accounts_payable_summary"). */
    String table();

    /**
     * 操作種別 (例: "verify", "mf_apply", "import", "INSERT", "UPDATE", "DELETE").
     * 監査ログ画面のフィルタ・分析で使う。
     */
    String operation();

    /**
     * 単一引数 PK の場合の引数 index (旧 API、後方互換)。
     * 0..n: その index の引数を JSONB 化。
     * -1 (既定): {@link #pkExpression()} を優先評価。両方未指定なら空 PK。
     */
    int pkArgIndex() default -1;

    /**
     * 複合 PK 用の SpEL 式。
     * <ul>
     *   <li>引数を {@code #a0}, {@code #a1}, ... または引数名で参照可能 (引数名は debug info 有時のみ)</li>
     *   <li>例: {@code "{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}"}</li>
     *   <li>戻り値は {@code Map<String, Object>} を想定。{@link AuditEntityLoader} 実装と同じキー名で揃える</li>
     * </ul>
     */
    String pkExpression() default "";

    /**
     * 戻り値を after snapshot として記録するか。
     * <p>{@link AuditEntityLoader} が登録されている table では、Loader 経由の after snapshot
     * (= DB から再 fetch した実 Entity) が優先される。本フラグは Loader が無い場合や
     * 戻り値に追加情報 (集計件数など) がある場合のフォールバック。
     */
    boolean captureReturnAsAfter() default true;

    /**
     * 全引数を補助情報として記録するか。
     * <ul>
     *   <li>true: {@code reason} 列に {@code "args=...JSON..."} を補助記録 (PK と after は別途)</li>
     *   <li>false (既定): 補助記録なし</li>
     * </ul>
     * <p>C4/C5 修正前は after_values に直接書き込んでいたが、実 before/after との混乱を避けるため
     * 補助情報扱いに変更した。
     */
    boolean captureArgsAsAfter() default false;
}
