package jp.co.oda32.constant;

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
}
