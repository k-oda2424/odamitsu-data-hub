package jp.co.oda32.batch.stock;

import jp.co.oda32.constant.StockLogReason;
import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.service.goods.CommonSalesGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.Chunk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 棚卸ファイル取り込みバッチWriterクラス
 *
 * @author k_oda
 * @since 2019/07/15
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class InventoryFileWriter extends AbstractInvoiceDateManagement implements ItemWriter<ExtInventoryFile> {
    @NonNull
    private CommonSalesGoodsService commonSalesGoodsService;
    @NonNull
    private StockManager stockManager;

    private Integer shopNo;
    private MWarehouse mWarehouse;

    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * @param items items to be written
     * @throws Exception if there are errors. The framework will catch the
     *                   exception and convert or rethrow it as appropriate.
     */
    @Override
    public void write(Chunk<? extends ExtInventoryFile> items) throws Exception {

        Map<String, BigDecimal> goodsStockMap = new HashMap<>();
        for (ExtInventoryFile item : items) {
            log.info(String.format("商品コード:%s,商品名:%s,棚卸在庫数:%s"
                    , item.get商品コード(), item.get商品名(), item.get実地棚卸数量()));
            goodsStockMap.put(item.get商品コード(), item.get実地棚卸数量());
            if (this.shopNo == null) {
                this.shopNo = item.getShopNo();
            }
            if (this.mWarehouse == null) {
                this.mWarehouse = item.getMWarehouse();
            }
        }
        log.info("商品検索中・・・・");
        List<ISalesGoods> salesGoodsList = this.commonSalesGoodsService.findExistSalesGoods(new ArrayList<>(goodsStockMap.keySet()), this.shopNo);
        Map<String, Integer> goodsNoMap = salesGoodsList.stream().collect(Collectors.toMap(ISalesGoods::getGoodsCode, ISalesGoods::getGoodsNo));
        goodsStockMap.forEach((goodsCode, stockNum) -> {
            if (!goodsNoMap.containsKey(goodsCode)) {
                log.warn(String.format("販売商品に登録のない商品です。商品登録をしてください。商品コード:%s", goodsCode));
                return;
            }
            int goodsNo = goodsNoMap.get(goodsCode);
            try {
                LocalDateTime inventoryTime = getInventoryDateTime();
                log.info(String.format("在庫処理中・・・商品コード:%s 棚卸在庫数:%s", goodsCode, stockNum));
                // 棚卸し上書き処理
                this.stockManager.overwriteStock(goodsNo, this.mWarehouse.getWarehouseNo(), this.mWarehouse.getCompanyNo(), goodsStockMap.get(goodsCode), StockLogReason.INVENTORY, inventoryTime);
            } catch (Exception e) {
                e.printStackTrace();
                log.error(String.format("棚卸処理に失敗しました。商品コード:%s", goodsCode));
            }
        });
    }
}
