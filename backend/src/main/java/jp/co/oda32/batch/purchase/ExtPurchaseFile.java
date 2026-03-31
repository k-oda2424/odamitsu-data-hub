package jp.co.oda32.batch.purchase;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 取り込み用仕入ファイルフォーマットを拡張したクラス
 *
 * @author k_oda
 * @since 2019/06/27
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ExtPurchaseFile extends PurchaseFile {
    private Integer goodsNo;
    private Integer companyNo;
    private Integer supplierNo;
    private Integer warehouseNo;
}
