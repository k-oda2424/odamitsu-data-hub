package jp.co.oda32.batch.goods;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 取り込み用商品ファイルフォーマット
 *
 * @author k_oda
 * @since 2018/07/18
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GoodsFile {
    private String 商品コード;
    private String 商品名;
    private String 商品名索引;
    private String 単位;
    private String 個数単位;
    private BigDecimal 入数;
    private BigDecimal セット品区分;
    private String セット品区分名;
    private BigDecimal 自動振替処理対象区分;
    private String 自動振替処理対象区分名;
    private BigDecimal 在庫管理区分;
    private String 在庫管理区分名;
    private String 主仕入先コード;
    private String 主仕入先名;
    private BigDecimal 標準売上単価;
    private BigDecimal ランク１売上単価;
    private BigDecimal ランク２売上単価;
    private BigDecimal ランク３売上単価;
    private BigDecimal ランク４売上単価;
    private BigDecimal ランク５売上単価;
    private BigDecimal 標準仕入単価;
    private BigDecimal 在庫評価単価;
    private BigDecimal 粗利算出用単価;
    private BigDecimal 上代単価;
    private String 新単価実施日;
    private BigDecimal 変更後単価使用区分;
    private String 期間単価対象日付;
    private BigDecimal 新標準売上単価;
    private BigDecimal 新ランク１売上単価;
    private BigDecimal 新ランク２売上単価;
    private BigDecimal 新ランク３売上単価;
    private BigDecimal 新ランク４売上単価;
    private BigDecimal 新ランク５売上単価;
    private BigDecimal 新標準仕入単価;
    private BigDecimal 新在庫評価単価;
    private BigDecimal 新粗利算出用単価;
    private BigDecimal 新上代単価;
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
    private BigDecimal 非課税区分;
    private String 非課税区分名;
    private BigDecimal 売上単価設定区分;
    private String 売上単価設定区分名;
    private BigDecimal 仕入単価設定区分;
    private String 仕入単価設定区分名;
    private BigDecimal 上代単価設定区分;
    private String 上代単価設定区分名;
    private BigDecimal 新売上単価設定区分;
    private String 新売上単価設定区分名;
    private BigDecimal 新仕入単価設定区分;
    private String 新仕入単価設定区分名;
    private BigDecimal 新上代単価設定区分;
    private String 新上代単価設定区分名;
    private BigDecimal 売上込変換計算区分;
    private String 売上込変換計算区分名;
    private BigDecimal 売上込変換計算単位;
    private BigDecimal 売上抜変換計算区分;
    private String 売上抜変換計算区分名;
    private BigDecimal 売上抜変換計算単位;
    private BigDecimal 仕入込変換計算区分;
    private String 仕入込変換計算区分名;
    private BigDecimal 仕入込変換計算単位;
    private BigDecimal 仕入抜変換計算区分;
    private String 仕入抜変換計算区分名;
    private BigDecimal 仕入抜変換計算単位;
    private BigDecimal 上代込変換計算区分;
    private String 上代込変換計算区分名;
    private BigDecimal 上代込変換計算単位;
    private BigDecimal 上代抜変換計算区分;
    private String 上代抜変換計算区分名;
    private BigDecimal 上代抜変換計算単位;
    private BigDecimal 消費税率区分;
    private BigDecimal 旧税率;
    private BigDecimal 新税率;
    private String 税率実施年月日;
    private BigDecimal 適正在庫数量;
    private BigDecimal 期首残数量;
    private BigDecimal 期首残金額;
    private BigDecimal マスター検索表示区分;
    private String マスター検索表示区分名;
    private String 入力パターンNo売上;
    private String 入力パターン名売上;
    private String 入力パターンNo仕入;
    private String 入力パターン名仕入;
    private String ＪＡＮ;
    private BigDecimal 期間限定上代単価;
    private String 有効期間開始日;
    private String 有効期間終了日;
    private String 経過措置指定日;
    private BigDecimal 旧税分類;
    private String 旧税分類名;
    private int 新税分類;
    private String 新税分類名;
    private String 操作日付;
    private String ログインＩＤ;
    private String ログイン名;

    public static String[] getGoodsFileFormat() {
        return new String[]{"商品コード",
                "商品名",
                "商品名索引",
                "単位",
                "個数単位",
                "入数",
                "セット品区分",
                "セット品区分名",
                "自動振替処理対象区分",
                "自動振替処理対象区分名",
                "在庫管理区分",
                "在庫管理区分名",
                "主仕入先コード",
                "主仕入先名",
                "標準売上単価",
                "ランク１売上単価",
                "ランク２売上単価",
                "ランク３売上単価",
                "ランク４売上単価",
                "ランク５売上単価",
                "標準仕入単価",
                "在庫評価単価",
                "粗利算出用単価",
                "上代単価",
                "新単価実施日",
                "変更後単価使用区分",
                "期間単価対象日付",
                "新標準売上単価",
                "新ランク１売上単価",
                "新ランク２売上単価",
                "新ランク３売上単価",
                "新ランク４売上単価",
                "新ランク５売上単価",
                "新標準仕入単価",
                "新在庫評価単価",
                "新粗利算出用単価",
                "新上代単価",
                "メーカーコード",
                "メーカー名",
                "商品分類コード",
                "商品分類名",
                "商品分類２コード",
                "商品分類２名",
                "商品分類３コード",
                "商品分類３名",
                "商品分類４コード",
                "商品分類４名",
                "商品分類５コード",
                "商品分類５名",
                "商品分類６コード",
                "商品分類６名",
                "商品分類７コード",
                "商品分類７名",
                "商品分類８コード",
                "商品分類８名",
                "商品分類９コード",
                "商品分類９名",
                "非課税区分",
                "非課税区分名",
                "売上単価設定区分",
                "売上単価設定区分名",
                "仕入単価設定区分",
                "仕入単価設定区分名",
                "上代単価設定区分",
                "上代単価設定区分名",
                "新売上単価設定区分",
                "新売上単価設定区分名",
                "新仕入単価設定区分",
                "新仕入単価設定区分名",
                "新上代単価設定区分",
                "新上代単価設定区分名",
                "売上込変換計算区分",
                "売上込変換計算区分名",
                "売上込変換計算単位",
                "売上抜変換計算区分",
                "売上抜変換計算区分名",
                "売上抜変換計算単位",
                "仕入込変換計算区分",
                "仕入込変換計算区分名",
                "仕入込変換計算単位",
                "仕入抜変換計算区分",
                "仕入抜変換計算区分名",
                "仕入抜変換計算単位",
                "上代込変換計算区分",
                "上代込変換計算区分名",
                "上代込変換計算単位",
                "上代抜変換計算区分",
                "上代抜変換計算区分名",
                "上代抜変換計算単位",
                "消費税率区分",
                "旧税率",
                "新税率",
                "税率実施年月日",
                "適正在庫数量",
                "期首残数量",
                "期首残金額",
                "マスター検索表示区分",
                "マスター検索表示区分名",
                "入力パターンNo売上",
                "入力パターン名売上",
                "入力パターンNo仕入",
                "入力パターン名仕入",
                "ＪＡＮ",
                "期間限定上代単価",
                "有効期間開始日",
                "有効期間終了日",
                "経過措置指定日",
                "旧税分類",
                "旧税分類名",
                "新税分類",
                "新税分類名",
                "操作日付",
                "ログインＩＤ",
                "ログイン名"};
    }
}
