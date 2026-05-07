package jp.co.oda32.constant;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 財務／買掛金関連の定数。
 * <p>
 * 買掛金集計と仕入一覧で同一の除外ルールを使うため、ここに集約する。
 * 個別の Controller / Service / Batch で同じ値をハードコードしないこと。
 */
public final class FinanceConstants {

    private FinanceConstants() {}

    /**
     * 買掛金集計・仕入一覧から除外する仕入先 No。
     * 既存運用で特殊仕入先として集計対象外にしている。
     */
    public static final int EXCLUDED_SUPPLIER_NO = 303;

    /**
     * 買掛金集計の対象ショップ。
     * <p>第1事業部(shop_no=1) に仕入支払先マスタを集約する運用に伴い、
     * 買掛金は shop_no=1 の仕入分のみを集計対象とする。shop_no=2(第2事業部)は
     * 会計系統が別で、買掛管理は本システム外（別運用）で行うため除外する。
     * 将来的にマルチテナントで買掛管理対象が増える場合は Set 型に拡張すること。
     */
    public static final int ACCOUNTS_PAYABLE_SHOP_NO = 1;

    /**
     * 第2事業部の月次集約仕入を表す商品コード。
     * <p>SMILE では第1事業部と第2事業部がシステム分離されており、第2事業部の
     * 個別仕入は別系統で第1事業部DBに取り込まれる。加えて運用上、各仕入先ごとに
     * 第2事業部分の「1ヶ月分合計」を本DBに手入力するケースがあり、その際に
     * 以下の商品コードが使われる:
     * <ul>
     *   <li>{@code 00000021} — 第2事業部 10%課税商品の月次集約</li>
     *   <li>{@code 00000023} — 第2事業部 8%課税商品の月次集約</li>
     * </ul>
     * これらは個別仕入と重複する（ダブルカウント）ため、買掛金集計・仕入集計の
     * 両方で除外する必要がある。
     */
    public static final Set<String> DIVISION2_AGGREGATE_GOODS_CODES =
            Set.of("00000021", "00000023");

    /**
     * 検証済みCSV出力プレビューで「5日払い相当 / 20日払い相当 Excel が未取込か」を
     * 判定する際の月内境界日 (exclusive)。翌月 {@code [1, CUTOFF)} を 5日払い相当、
     * {@code [CUTOFF, 月末]} を 20日払い相当とみなす。
     * <p>土日祝による振替で 5日 が 4日/6日/7日、20日 が 19日/21日 等に前後するため、
     * 単純に日付一致で判定せず「前半/後半」で包括的にカバーする。
     */
    public static final int PAYMENT_DATE_MIDMONTH_CUTOFF = 15;

    /**
     * 買掛金一覧における一括検証由来の備考接頭辞 (UI 表示用)。
     * <p>{@code PaymentMfImportService#applyVerification} が書き込む備考の先頭文字列。
     *
     * @deprecated G2-M10 (2026-05-06): 「BULK か MANUAL か」の判定には本接頭辞ではなく
     *     {@code t_accounts_payable_summary.verification_source} 列
     *     ({@link #VERIFICATION_SOURCE_BULK} / {@link #VERIFICATION_SOURCE_MANUAL}) を使うこと。
     *     ユーザが偶然この接頭辞で始まる note を手入力すると bulk と誤判定され、
     *     再 upload による上書き保護が外れるリスクがある (V040 で source 列を追加済)。
     *     本定数は note の <b>表示用接頭辞生成</b>のみに使い、判定には使用しないこと。
     */
    @Deprecated
    public static final String VERIFICATION_NOTE_BULK_PREFIX = "振込明細検証 ";

    // -------------------------------------------------------------
    // G2-M1 / G2-M10 (2026-05-06): 検証値の書込経路 enum (V040 で列追加)
    // -------------------------------------------------------------

    /**
     * 検証値 (verified_amount / verified_amount_tax_excluded) の書込経路: 振込明細 Excel 一括検証由来。
     * <p>{@code PaymentMfImportService#applyVerification} が書込時にセット。
     * 同 (shop, supplier, transactionMonth) の全税率行に同値の集約値が冗長保持される。
     * read 側 ({@code sumVerifiedAmountForGroup}) で「全行 BULK → 代表値 1 度」と扱う。
     */
    public static final String VERIFICATION_SOURCE_BULK = "BULK_VERIFICATION";

    /**
     * 検証値の書込経路: UI 手入力単一 PK 更新由来。
     * <p>{@code TAccountsPayableSummaryService#verify} が書込時にセット。
     * 単一 (shop, supplier, txMonth, taxRate) 行のみ更新するため、税率別に異なる値が入りうる。
     * read 側で SUM 集計する。1 行でも本値を含むグループは bulk 集約値とみなさない。
     */
    public static final String VERIFICATION_SOURCE_MANUAL = "MANUAL_VERIFICATION";

    /**
     * 検証値の書込経路: 整合性レポート MF override 由来 (税率別按分)。
     * <p>{@code ConsistencyReviewService#applyMfOverride} が書込時にセット。
     * 税率別 change 比で按分するため税率別に異なる値が入る。read 側で SUM 集計する。
     */
    public static final String VERIFICATION_SOURCE_MF_OVERRIDE = "MF_OVERRIDE";

    /**
     * G2-M2 (2026-05-06): 買掛仕入 MF 振込明細の per-supplier 1 円整合性違反を示すエラーコード。
     * <p>{@code FinanceBusinessException(message, code)} で投げ、
     * {@code FinanceExceptionHandler} がこのコードを検出して 422 + 業務メッセージで応答する。
     * <p>クライアント側はこのコードで「force=true で再実行」UI 分岐を表示する。
     */
    public static final String ERROR_CODE_PER_SUPPLIER_MISMATCH = "PER_SUPPLIER_MISMATCH";

    /**
     * Codex Major #4 (2026-05-06): {@code force=true} 指定時の業務理由が未指定であることを示すエラーコード。
     * <p>強制反映は破壊的操作のため、{@code PaymentMfApplyRequest.forceReason} が
     * 空文字 / null の場合は {@code FinanceBusinessException(code=FORCE_REASON_REQUIRED)} を投げて 400 拒否する。
     * <p>クライアント側はこのコードを検出して「強制反映の理由を入力してください」ダイアログを表示する。
     */
    public static final String ERROR_CODE_FORCE_REASON_REQUIRED = "FORCE_REASON_REQUIRED";

    /**
     * 買掛金 vs SMILE 支払額の差額が「自動一致」とみなせる円未満の境界 (exclusive)。
     * <p>これより小さい絶対値の差額は、丸め誤差・手数料調整等の誤差範囲として
     * 「一致」判定し、SMILE 支払額に自動で合わせる。
     */
    public static final BigDecimal PAYMENT_VERIFICATION_TOLERANCE = new BigDecimal(5);

    /**
     * 買掛金照合レポートで「軽微な差額」として強調表示する境界 (inclusive)。
     * <p>買掛金一覧の手入力検証・一括検証の一致判定閾値もこの値と共通。値を変えると
     * Excel 一括検証 / 手入力 / レポートの判定がまとめて変わる点に注意。
     */
    public static final BigDecimal PAYMENT_REPORT_MINOR_DIFFERENCE = new BigDecimal(100);

    /** {@link #PAYMENT_REPORT_MINOR_DIFFERENCE} の long 版 (long 比較したい箇所向け)。 */
    public static final long PAYMENT_REPORT_MINOR_DIFFERENCE_LONG = PAYMENT_REPORT_MINOR_DIFFERENCE.longValueExact();

    /**
     * 買掛金照合レポートで「中程度の差額」として強調表示する境界 (inclusive)。
     */
    public static final BigDecimal PAYMENT_REPORT_MEDIUM_DIFFERENCE = new BigDecimal(1000);

    /**
     * 買掛 / 売掛 verified_amount 突合の許容差 (税込円)。
     * <p>設計書 D §3.6 / 整合性 §3.2 で定義。これより大きい絶対値の差額は「不一致」と判定し、
     * verification_result=0 とする。{@link #PAYMENT_REPORT_MINOR_DIFFERENCE} と同値だが
     * 用途 (照合判定 vs UI 強調表示) が異なるため別名で公開。
     */
    public static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);

    /**
     * 売掛金 / 請求書 (t_invoice) 突合の許容誤差 (税込円) のデフォルト値 (SF-E07)。
     * <p>{@code application.yml} の {@code batch.accounts-receivable.invoice-amount-tolerance} で
     * 個別環境では上書きでき、未指定時は本値 (3 円) が採用される
     * ({@code InvoiceVerifier#invoiceAmountTolerance} の {@code @Value} デフォルト参照先)。
     * <p>{@link #MATCH_TOLERANCE} (買掛 100 円) と用途・閾値が異なるため別名で公開。
     */
    public static final BigDecimal INVOICE_AMOUNT_TOLERANCE_DEFAULT = BigDecimal.valueOf(3);
}
