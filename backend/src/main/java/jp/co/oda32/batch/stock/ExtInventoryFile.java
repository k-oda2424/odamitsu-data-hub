package jp.co.oda32.batch.stock;

import jp.co.oda32.domain.model.master.MWarehouse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
public class ExtInventoryFile extends InventoryFile {
    private Integer shopNo;
    private MWarehouse mWarehouse;
}
