package jp.co.oda32.batch.stock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 棚卸しファイルフォーマット
 *
 * @author k_oda
 * @since 2019/07/15
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InventoryFile {
    private String 商品コード;
    private String 商品名;
    private String 単位;
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
    private BigDecimal 在庫評価単価;
    private BigDecimal 帳簿在庫数;
    private BigDecimal 実地棚卸数量;


    public static String[] getInventoryFileFormat() {
        return new String[]{
                "商品コード",
                "商品名",
                "単位",
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
                "在庫評価単価",
                "帳簿在庫数",
                "実地棚卸数量"
        };
    }
}
