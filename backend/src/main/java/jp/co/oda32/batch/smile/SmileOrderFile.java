package jp.co.oda32.batch.smile;

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
 * 取り込み用注文ファイルフォーマット
 * SMILEからの取り込み
 * SMILEの項目はすべてローマ字で定義する
 *
 * @author k_oda
 * @since 2018/11/20
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SmileOrderFile implements ISmileFile {
    String 伝票日付;
    String 年月度;
    BigDecimal 伝票番号;
    Long 処理連番;
    BigDecimal 明細区分;
    String 明細区分名;
    Integer 行;
    String 得意先コード;
    String 得意先名1;
    String 得意先名2;
    String 得意先名略称;
    String 得意先営業所コード;
    String 得意先営業所名;
    String 得意先部門コード;
    String 得意先部門名;
    String 得意先地区コード;
    String 得意先地区名;
    String 得意先業種コード;
    String 得意先業種名;
    String 得意先グループコード;
    String 得意先グループ名;
    String 得意先単価ランクコード;
    String 得意先単価ランク名;
    String 得意先分類6コード;
    String 得意先分類6名;
    String 得意先分類7コード;
    String 得意先分類7名;
    String 得意先分類8コード;
    String 得意先分類8名;
    String 得意先分類9コード;
    String 得意先分類9名;
    String 請求先コード;
    String 請求先名;
    String 請求先営業所コード;
    String 請求先営業所名;
    String 請求先部門コード;
    String 請求先部門名;
    String 請求先地区コード;
    String 請求先地区名;
    String 請求先業種コード;
    String 請求先業種名;
    String 請求先グループコード;
    String 請求先グループ名;
    String 請求先単価ランクコード;
    String 請求先単価ランク名;
    String 請求先分類6コード;
    String 請求先分類6名;
    String 請求先分類7コード;
    String 請求先分類7名;
    String 請求先分類8コード;
    String 請求先分類8名;
    String 請求先分類9コード;
    String 請求先分類9名;
    String 納品先コード;
    String 納品先名;
    String 担当者コード;
    String 担当者名;
    String 担当者分類0コード;
    String 担当者分類0名;
    String 担当者分類1コード;
    String 担当者分類1名;
    String 担当者分類2コード;
    String 担当者分類2名;
    String 担当者分類3コード;
    String 担当者分類3名;
    String 担当者分類4コード;
    String 担当者分類4名;
    String 担当者分類5コード;
    String 担当者分類5名;
    String 担当者分類6コード;
    String 担当者分類6名;
    String 担当者分類7コード;
    String 担当者分類7名;
    String 担当者分類8コード;
    String 担当者分類8名;
    String 担当者分類9コード;
    String 担当者分類9名;
    BigDecimal 請求;
    String 請求区分名;
    BigDecimal 売掛区分;
    String 売掛区分名;
    BigDecimal 取引区分;
    String 取引区分名;
    BigDecimal 取引区分属性;
    String 取引区分属性名;
    String 商品コード;
    String 商品名;
    String メーカーコード;
    String メーカー名;
    String 商品分類コード;
    String 商品分類名;
    String 商品分類2コード;
    String 商品分類2名;
    String 商品分類3コード;
    String 商品分類3名;
    String 商品分類4コード;
    String 商品分類4名;
    String 商品分類5コード;
    String 商品分類5名;
    String 商品分類6コード;
    String 商品分類6名;
    String 商品分類7コード;
    String 商品分類7名;
    String 商品分類8コード;
    String 商品分類8名;
    String 商品分類9コード;
    String 商品分類9名;
    BigDecimal 入数;
    // ケース注文数量
    BigDecimal 個数;
    String 個数単位;
    BigDecimal 数量;
    String 数量単位;
    BigDecimal 単価;
    BigDecimal 金額;
    BigDecimal 原単価;
    BigDecimal 原価金額;
    BigDecimal 粗利;
    BigDecimal 単価掛率;
    BigDecimal 課税区分;
    String 課税区分名;
    BigDecimal 消費税率;
    BigDecimal 内消費税等;
    String 行摘要コード;
    String 行摘要1;
    String 行摘要2;
    String 備考コード;
    String 備考;
    String ログインＩＤ;
    String ログイン名;
    String 操作日付;
    BigDecimal 受注番号;
    BigDecimal 受注行;
    String オーダー番号;
    BigDecimal 見積処理連番;
    BigDecimal 見積行;
    BigDecimal 自動生成区分;
    String 自動生成区分名;
    BigDecimal 伝票消費税計算区分;
    String 伝票消費税計算区分名;
    BigDecimal データ発生区分;
    BigDecimal 相手処理連番;
    String 入力パターン番号;
    String 入力パターン名;
    String 不使用伝票番号;
    String 相手伝票番号;
    String コード;
    String 不使用課税区分;
    String 不使用コード;
    String 直送区分;
    String 社店コード;
    String 分類コード;
    String 伝票区分;
    String 取引先コード;
    BigDecimal 売単価;
    String 相手商品コード;
    BigDecimal チェックマーク区分;
    String チェックマーク区分名;
    Integer 消費税分類;
    String 消費税分類名;

    public String get伝票番号() {
        return this.伝票番号.toString();
    }

    public String get明細区分() {
        return this.明細区分.toString();
    }

    public String get売掛区分() {
        return this.売掛区分.toString();
    }

    public String get課税区分() {
        return this.課税区分.toString();
    }

    public LocalDate get伝票日付() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
        return DateTimeUtil.stringToLocalDate(this.伝票日付, formatter);
    }

    public static String[] getSmileOrderFileFormat() {
        return new String[]{
                "伝票日付",
                "年月度",
                "伝票番号",
                "処理連番",
                "明細区分",
                "明細区分名",
                "行",
                "得意先コード",
                "得意先名1",
                "得意先名2",
                "得意先名略称",
                "得意先営業所コード",
                "得意先営業所名",
                "得意先部門コード",
                "得意先部門名",
                "得意先地区コード",
                "得意先地区名",
                "得意先業種コード",
                "得意先業種名",
                "得意先グループコード",
                "得意先グループ名",
                "得意先単価ランクコード",
                "得意先単価ランク名",
                "得意先分類6コード",
                "得意先分類6名",
                "得意先分類7コード",
                "得意先分類7名",
                "得意先分類8コード",
                "得意先分類8名",
                "得意先分類9コード",
                "得意先分類9名",
                "請求先コード",
                "請求先名",
                "請求先営業所コード",
                "請求先営業所名",
                "請求先部門コード",
                "請求先部門名",
                "請求先地区コード",
                "請求先地区名",
                "請求先業種コード",
                "請求先業種名",
                "請求先グループコード",
                "請求先グループ名",
                "請求先単価ランクコード",
                "請求先単価ランク名",
                "請求先分類6コード",
                "請求先分類6名",
                "請求先分類7コード",
                "請求先分類7名",
                "請求先分類8コード",
                "請求先分類8名",
                "請求先分類9コード",
                "請求先分類9名",
                "納品先コード",
                "納品先名",
                "担当者コード",
                "担当者名",
                "担当者分類0コード",
                "担当者分類0名",
                "担当者分類1コード",
                "担当者分類1名",
                "担当者分類2コード",
                "担当者分類2名",
                "担当者分類3コード",
                "担当者分類3名",
                "担当者分類4コード",
                "担当者分類4名",
                "担当者分類5コード",
                "担当者分類5名",
                "担当者分類6コード",
                "担当者分類6名",
                "担当者分類7コード",
                "担当者分類7名",
                "担当者分類8コード",
                "担当者分類8名",
                "担当者分類9コード",
                "担当者分類9名",
                "請求",
                "請求区分名",
                "売掛区分",
                "売掛区分名",
                "取引区分",
                "取引区分名",
                "取引区分属性",
                "取引区分属性名",
                "商品コード",
                "商品名",
                "メーカーコード",
                "メーカー名",
                "商品分類コード",
                "商品分類名",
                "商品分類2コード",
                "商品分類2名",
                "商品分類3コード",
                "商品分類3名",
                "商品分類4コード",
                "商品分類4名",
                "商品分類5コード",
                "商品分類5名",
                "商品分類6コード",
                "商品分類6名",
                "商品分類7コード",
                "商品分類7名",
                "商品分類8コード",
                "商品分類8名",
                "商品分類9コード",
                "商品分類9名",
                "入数",
                "個数",
                "個数単位",
                "数量",
                "数量単位",
                "単価",
                "金額",
                "原単価",
                "原価金額",
                "粗利",
                "単価掛率",
                "課税区分",
                "課税区分名",
                "消費税率",
                "内消費税等",
                "行摘要コード",
                "行摘要1",
                "行摘要2",
                "備考コード",
                "備考",
                "ログインＩＤ",
                "ログイン名",
                "操作日付",
                "受注番号",
                "受注行",
                "オーダー番号",
                "見積処理連番",
                "見積行",
                "自動生成区分",
                "自動生成区分名",
                "伝票消費税計算区分",
                "伝票消費税計算区分名",
                "データ発生区分",
                "相手処理連番",
                "入力パターン番号",
                "入力パターン名",
                "不使用伝票番号",
                "相手伝票番号",
                "コード",
                "不使用課税区分",
                "不使用コード",
                "直送区分",
                "社店コード",
                "分類コード",
                "伝票区分",
                "取引先コード",
                "売単価",
                "相手商品コード",
                "チェックマーク区分",
                "チェックマーク区分名",
                "消費税分類",
                "消費税分類名"
        };
    }

    int shopNo;
}
