package jp.co.oda32.batch.stock;

import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.service.master.MWarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 棚卸ファイル取込バッチProcessorクラス
 *
 * @author k_oda
 * @since 2019/07/15
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class InventoryFileProcessor implements ItemProcessor<InventoryFile, ExtInventoryFile> {
    @Value("#{jobParameters['shopNo']}")
    private Integer shopNo;
    @NonNull
    private MWarehouseService mWarehouseService;
    @Value("#{jobParameters['warehouseNo']}")
    private Integer warehouseNo;
    private MWarehouse mWarehouse;

    @Override
    public ExtInventoryFile process(InventoryFile item) throws Exception {
        if (this.shopNo == null) {
            throw new Exception("shop番号がnullです。バッチ起動引数に指定してください。 --shopNo 番号");
        }
        if (this.mWarehouse == null) {
            this.mWarehouse = this.mWarehouseService.getByWarehouseNo(this.warehouseNo);
            if (this.mWarehouse == null) {
                throw new Exception(String.format("存在しないwarehouseNoです。warehouseNo:%d", this.warehouseNo));
            }
        }
        ExtInventoryFile extInventoryFile = new ExtInventoryFile();
        BeanUtils.copyProperties(item, extInventoryFile);
        extInventoryFile.setShopNo(this.shopNo);
        extInventoryFile.setMWarehouse(mWarehouse);
        return extInventoryFile;
    }
}
