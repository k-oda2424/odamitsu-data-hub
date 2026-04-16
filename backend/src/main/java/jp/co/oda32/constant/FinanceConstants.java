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
     * 買掛金一覧における一括検証由来の備考接頭辞。
     * <p>{@code PaymentMfImportService#applyVerification} が書き込む備考の先頭文字列で、
     * UI/DTO 側で "BULK" (一括検証) と "MANUAL" (UI 手入力) を判別するために使う。
     */
    public static final String VERIFICATION_NOTE_BULK_PREFIX = "振込明細検証 ";

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
}
