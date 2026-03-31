package jp.co.oda32.batch.purchase;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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
public class PurchaseMasterFile {
    private String 仕入先コード;
    private String 仕入先名１;
    private String 仕入先名２;
    private String 仕入先名略称;
    private String 仕入先名索引;
    private String 郵便番号;
    private String 住所１;
    private String 住所２;
    private String 住所３;
    private String カスタマバーコード;
    private String 電話番号;
    private String ＦＡＸ番号;
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
    private BigDecimal 支払先区分;
    private String 支払先区分名;
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
    private BigDecimal 締日１;
    private BigDecimal 締日２;
    private BigDecimal 締日３;
    private BigDecimal 支払日１;
    private BigDecimal 支払日２;
    private BigDecimal 支払日３;
    private BigDecimal 支払サイクル１;
    private String 支払サイクル名１;
    private BigDecimal 支払サイクル２;
    private String 支払サイクル名２;
    private BigDecimal 支払サイクル３;
    private String 支払サイクル名３;
    private BigDecimal 支払条件１;
    private String 支払条件名１;
    private BigDecimal 支払条件２;
    private String 支払条件名２;
    private BigDecimal 支払条件３;
    private String 支払条件名３;
    private BigDecimal 単価掛率区分;
    private String 単価掛率区分名;
    private BigDecimal 単価掛率;
    private BigDecimal 単価処理区分;
    private String 単価処理区分名;
    private BigDecimal 単価処理単位;
    private BigDecimal 金額処理区分;
    private String 金額処理区分名;
    private BigDecimal 消費税免税区分;
    private String 消費税免税区分名;
    private BigDecimal 仕入単価設定区分;
    private String 仕入単価設定区分名;
    private BigDecimal 上代単価設定区分;
    private String 上代単価設定区分名;
    private BigDecimal 単価変換区分;
    private String 単価変換区分名;
    private BigDecimal 消費税通知区分;
    private String 消費税通知区分名;
    private BigDecimal 消費税計算区分;
    private String 消費税計算区分名;
    private BigDecimal 消費税計算単位;
    private BigDecimal 消費税分解区分;
    private String 消費税分解区分名;
    private BigDecimal 支払明細書出力タイプ;
    private String 支払明細書出力タイプ名;
    private BigDecimal 支払明細書出力形式;
    private String 支払明細書出力形式名;
    private BigDecimal 仕入先台帳出力形式;
    private String 仕入先台帳出力形式名;
    private BigDecimal 支払消費税算出単位;
    private String 支払消費税算出単位名;
    private BigDecimal 期首買掛残高;
    private BigDecimal 前回支払残高;
    private String 相殺得意先コード;
    private String 相殺得意先名;
    private BigDecimal 日付印字区分;
    private String 日付印字区分名;
    private String 相手先担当者名;
    private String 取引;
    private String 取引名;
    private String 注文書会社名パターン;
    private String 注文書会社名名称;
    private String 支払明細書会社名パターン;
    private String 支払明細書会社名名称;
    private BigDecimal マスター検索表示区分;
    private String マスター検索表示区分名;
    private BigDecimal 注文書出力区分;
    private String 注文書出力区分名;
    private BigDecimal 変換コード注文書出力区分;
    private String 変換コード注文書出力区分名;
    private BigDecimal 変換コード支払明細出力区分;
    private String 変換コード支払明細出力区分名;
    private BigDecimal 入力処理モード;
    private String 入力処理モード名;
    private BigDecimal 個別設定入力行数;
    private String 有効期間開始日;
    private String 有効期間終了日;

    // 追加された項目
    private String 操作日付;
    private String ログインID;
    private String ログイン名;
    private String 適格請求書発行事業者対象区分;
    private String 適格請求書発行事業者対象区分名;
    private String 登録番号;
    private String 登録番号支払明細書出力;
    private String 登録番号支払明細書出力名;

    public static String[] getPurchaseMasterFileFormat() {
        return new String[]{
                // 既存項目
                "仕入先コード", "仕入先名１", "仕入先名２", "仕入先名略称", "仕入先名索引", "郵便番号", "住所１", "住所２", "住所３", "カスタマバーコード",
                "電話番号", "ＦＡＸ番号", "仕入先営業所コード", "仕入先営業所名", "仕入先部門コード", "仕入先部門名", "仕入先地区コード", "仕入先地区名",
                "仕入先業種コード", "仕入先業種名", "仕入先分類４コード", "仕入先分類４名", "仕入先分類５コード", "仕入先分類５名", "仕入先分類６コード",
                "仕入先分類６名", "仕入先分類７コード", "仕入先分類７名", "仕入先分類８コード", "仕入先分類８名", "仕入先分類９コード", "仕入先分類９名",
                "支払先コード", "支払先名", "支払先区分", "支払先区分名", "支払先営業所コード", "支払先営業所名", "支払先部門コード", "支払先部門名",
                "支払先地区コード", "支払先地区名", "支払先業種コード", "支払先業種名", "支払先分類４コード", "支払先分類４名", "支払先分類５コード",
                "支払先分類５名", "支払先分類６コード", "支払先分類６名", "支払先分類７コード", "支払先分類７名", "支払先分類８コード", "支払先分類８名",
                "支払先分類９コード", "支払先分類９名", "担当者コード", "担当者名", "担当者分類０コード", "担当者分類０名", "担当者分類１コード",
                "担当者分類１名", "担当者分類２コード", "担当者分類２名", "担当者分類３コード", "担当者分類３名", "担当者分類４コード", "担当者分類４名",
                "担当者分類５コード", "担当者分類５名", "担当者分類６コード", "担当者分類６名", "担当者分類７コード", "担当者分類７名", "担当者分類８コード",
                "担当者分類８名", "担当者分類９コード", "担当者分類９名", "締日１", "締日２", "締日３", "支払日１", "支払日２", "支払日３", "支払サイクル１",
                "支払サイクル名１", "支払サイクル２", "支払サイクル名２", "支払サイクル３", "支払サイクル名３", "支払条件１", "支払条件名１",
                "支払条件２", "支払条件名２", "支払条件３", "支払条件名３", "単価掛率区分", "単価掛率区分名", "単価掛率", "単価処理区分", "単価処理区分名",
                "単価処理単位", "金額処理区分", "金額処理区分名", "消費税免税区分", "消費税免税区分名", "仕入単価設定区分", "仕入単価設定区分名",
                "上代単価設定区分", "上代単価設定区分名", "単価変換区分", "単価変換区分名", "消費税通知区分", "消費税通知区分名", "消費税計算区分",
                "消費税計算区分名", "消費税計算単位", "消費税分解区分", "消費税分解区分名", "支払明細書出力タイプ", "支払明細書出力タイプ名",
                "支払明細書出力形式", "支払明細書出力形式名", "仕入先台帳出力形式", "仕入先台帳出力形式名", "支払消費税算出単位", "支払消費税算出単位名",
                "期首買掛残高", "前回支払残高", "相殺得意先コード", "相殺得意先名", "日付印字区分", "日付印字区分名", "相手先担当者名", "取引", "取引名",
                "注文書会社名パターン", "注文書会社名名称", "支払明細書会社名パターン", "支払明細書会社名名称", "マスター検索表示区分", "マスター検索表示区分名",
                "注文書出力区分", "注文書出力区分名", "変換コード注文書出力区分", "変換コード注文書出力区分名", "変換コード支払明細出力区分",
                "変換コード支払明細出力区分名", "入力処理モード", "入力処理モード名", "個別設定入力行数", "有効期間開始日", "有効期間終了日",

                // 追加項目
                "操作日付", "ログインID", "ログイン名", "適格請求書発行事業者対象区分", "適格請求書発行事業者対象区分名", "登録番号",
                "登録番号支払明細書出力", "登録番号支払明細書出力名"
        };
    }
}
