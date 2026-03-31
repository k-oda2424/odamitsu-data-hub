package jp.co.oda32.batch.purchase;

import jp.co.oda32.batch.ISmileFile;
import jp.co.oda32.util.DateTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * 取り込み用仕入ファイルフォーマット
 *
 * @author k_oda
 * @since 2019/04/29
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PurchaseFile implements ISmileFile {
    private String 伝票日付;
    private String 年月度;
    private BigDecimal 伝票番号;
    private Long 処理連番;
    private int 明細区分;
    private String 明細区分名;
    private int 行;
    private String 仕入先コード;
    private String 仕入先名１;
    private String 仕入先名２;
    private String 仕入先名略称;
    private String 仕入先営業所コード;
    private String 仕入先営業所名;
    private String 仕入先部門コード;
    private String 仕入先部門名;
    private String 仕入先地区コード;
    private String 仕入先地区名;
    private String 仕入先業種コード;
    private String 仕入先業種名;
    private String 仕入先分類４コード;
    private String 仕入先分類４名;
    private String 仕入先分類５コード;
    private String 仕入先分類５名;
    private String 仕入先分類６コード;
    private String 仕入先分類６名;
    private String 仕入先分類７コード;
    private String 仕入先分類７名;
    private String 仕入先分類８コード;
    private String 仕入先分類８名;
    private String 仕入先分類９コード;
    private String 仕入先分類９名;
    private String 支払先コード;
    private String 支払先名;
    private String 支払先営業所コード;
    private String 支払先営業所名;
    private String 支払先部門コード;
    private String 支払先部門名;
    private String 支払先地区コード;
    private String 支払先地区名;
    private String 支払先業種コード;
    private String 支払先業種名;
    private String 支払先分類４コード;
    private String 支払先分類４名;
    private String 支払先分類５コード;
    private String 支払先分類５名;
    private String 支払先分類６コード;
    private String 支払先分類６名;
    private String 支払先分類７コード;
    private String 支払先分類７名;
    private String 支払先分類８コード;
    private String 支払先分類８名;
    private String 支払先分類９コード;
    private String 支払先分類９名;
    private String 担当者コード;
    private String 担当者名;
    private String 担当者分類０コード;
    private String 担当者分類０名;
    private String 担当者分類１コード;
    private String 担当者分類１名;
    private String 担当者分類２コード;
    private String 担当者分類２名;
    private String 担当者分類３コード;
    private String 担当者分類３名;
    private String 担当者分類４コード;
    private String 担当者分類４名;
    private String 担当者分類５コード;
    private String 担当者分類５名;
    private String 担当者分類６コード;
    private String 担当者分類６名;
    private String 担当者分類７コード;
    private String 担当者分類７名;
    private String 担当者分類８コード;
    private String 担当者分類８名;
    private String 担当者分類９コード;
    private String 担当者分類９名;
    private BigDecimal 支払;
    private String 支払区分名;
    private BigDecimal 買掛区分;
    private String 買掛区分名;
    private BigDecimal 取引区分;
    private String 取引区分名;
    private BigDecimal 取引区分属性;
    private String 取引区分属性名;
    private String 商品コード;
    private String 商品名;
    private String メーカーコード;
    private String メーカー名;
    private String 商品分類コード;
    private String 商品分類名;
    private String 商品分類２コード;
    private String 商品分類２名;
    private String 商品分類３コード;
    private String 商品分類３名;
    private String 商品分類４コード;
    private String 商品分類４名;
    private String 商品分類５コード;
    private String 商品分類５名;
    private String 商品分類６コード;
    private String 商品分類６名;
    private String 商品分類７コード;
    private String 商品分類７名;
    private String 商品分類８コード;
    private String 商品分類８名;
    private String 商品分類９コード;
    private String 商品分類９名;
    private BigDecimal 入数;
    private BigDecimal 個数;
    private String 個数単位;
    private BigDecimal 数量;
    private String 数量単位;
    private BigDecimal 単価;
    private BigDecimal 金額;
    private BigDecimal 単価掛率;
    private String 課税区分;
    private String 課税区分名;
    private BigDecimal 消費税率;
    private BigDecimal 内消費税等;
    private String 行摘要コード;
    private String 行摘要１;
    private String 行摘要２;
    private String 備考コード;
    private String 備考;
    private String ログインID;
    private String ログイン名;
    private String 操作日付;
    private BigDecimal 発注番号;
    private BigDecimal 発注行;
    private String オーダーNo;
    private BigDecimal 自動生成区分;
    private String 自動生成区分名;
    private BigDecimal 伝票消費税計算区分;
    private String 伝票消費税計算区分名;
    private BigDecimal データ発生区分;
    private BigDecimal 相手処理連番;
    private String 入力パターンNo;
    private String 入力パターン名;
    private BigDecimal チェックマーク区分;
    private String チェックマーク区分名;
    private Integer 消費税分類;
    private String 消費税分類名;

    public static String[] getPurchaseFileFormat() {
        return new String[]{
                "伝票日付"
                , "年月度"
                , "伝票番号"
                , "処理連番"
                , "明細区分"
                , "明細区分名"
                , "行"
                , "仕入先コード"
                , "仕入先名１"
                , "仕入先名２"
                , "仕入先名略称"
                , "仕入先営業所コード"
                , "仕入先営業所名"
                , "仕入先部門コード"
                , "仕入先部門名"
                , "仕入先地区コード"
                , "仕入先地区名"
                , "仕入先業種コード"
                , "仕入先業種名"
                , "仕入先分類４コード"
                , "仕入先分類４名"
                , "仕入先分類５コード"
                , "仕入先分類５名"
                , "仕入先分類６コード"
                , "仕入先分類６名"
                , "仕入先分類７コード"
                , "仕入先分類７名"
                , "仕入先分類８コード"
                , "仕入先分類８名"
                , "仕入先分類９コード"
                , "仕入先分類９名"
                , "支払先コード"
                , "支払先名"
                , "支払先営業所コード"
                , "支払先営業所名"
                , "支払先部門コード"
                , "支払先部門名"
                , "支払先地区コード"
                , "支払先地区名"
                , "支払先業種コード"
                , "支払先業種名"
                , "支払先分類４コード"
                , "支払先分類４名"
                , "支払先分類５コード"
                , "支払先分類５名"
                , "支払先分類６コード"
                , "支払先分類６名"
                , "支払先分類７コード"
                , "支払先分類７名"
                , "支払先分類８コード"
                , "支払先分類８名"
                , "支払先分類９コード"
                , "支払先分類９名"
                , "担当者コード"
                , "担当者名"
                , "担当者分類０コード"
                , "担当者分類０名"
                , "担当者分類１コード"
                , "担当者分類１名"
                , "担当者分類２コード"
                , "担当者分類２名"
                , "担当者分類３コード"
                , "担当者分類３名"
                , "担当者分類４コード"
                , "担当者分類４名"
                , "担当者分類５コード"
                , "担当者分類５名"
                , "担当者分類６コード"
                , "担当者分類６名"
                , "担当者分類７コード"
                , "担当者分類７名"
                , "担当者分類８コード"
                , "担当者分類８名"
                , "担当者分類９コード"
                , "担当者分類９名"
                , "支払"
                , "支払区分名"
                , "買掛区分"
                , "買掛区分名"
                , "取引区分"
                , "取引区分名"
                , "取引区分属性"
                , "取引区分属性名"
                , "商品コード"
                , "商品名"
                , "メーカーコード"
                , "メーカー名"
                , "商品分類コード"
                , "商品分類名"
                , "商品分類２コード"
                , "商品分類２名"
                , "商品分類３コード"
                , "商品分類３名"
                , "商品分類４コード"
                , "商品分類４名"
                , "商品分類５コード"
                , "商品分類５名"
                , "商品分類６コード"
                , "商品分類６名"
                , "商品分類７コード"
                , "商品分類７名"
                , "商品分類８コード"
                , "商品分類８名"
                , "商品分類９コード"
                , "商品分類９名"
                , "入数"
                , "個数"
                , "個数単位"
                , "数量"
                , "数量単位"
                , "単価"
                , "金額"
                , "単価掛率"
                , "課税区分"
                , "課税区分名"
                , "消費税率"
                , "内消費税等"
                , "行摘要コード"
                , "行摘要１"
                , "行摘要２"
                , "備考コード"
                , "備考"
                , "ログインID"
                , "ログイン名"
                , "操作日付"
                , "発注番号"
                , "発注行"
                , "オーダーNo"
                , "自動生成区分"
                , "自動生成区分名"
                , "伝票消費税計算区分"
                , "伝票消費税計算区分名"
                , "データ発生区分"
                , "相手処理連番"
                , "入力パターンNo"
                , "入力パターン名"
                , "チェックマーク区分"
                , "チェックマーク区分名"
                , "消費税分類"
                , "消費税分類名"
        };
    }

    /**
     * ショップ番号保持用
     */
    int shopNo;

    public LocalDate get伝票日付() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
        return DateTimeUtil.stringToLocalDate(this.伝票日付, formatter);
    }

    public String get伝票日付Str() {
        return this.伝票日付;
    }
}
