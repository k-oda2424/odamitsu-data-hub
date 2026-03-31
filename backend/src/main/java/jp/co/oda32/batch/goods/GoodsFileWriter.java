package jp.co.oda32.batch.goods;

import jp.co.oda32.batch.goods.logic.GoodsFileWriterFactory;
import jp.co.oda32.batch.goods.logic.IGoodsFileWriterLogic;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.Chunk;

import java.util.List;

/**
 * 商品ファイル取り込みバッチWriterクラス
 * @author k_oda
 * @since 2018/07/19
 */
@Component
@StepScope
@Log4j2
public class GoodsFileWriter implements ItemWriter<GoodsFile> {
    @Autowired
    private GoodsFileWriterFactory goodsFileWriterFactory;

    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * @param items items to be written
     * @throws Exception if there are errors. The framework will catch the
     *                   exception and convert or rethrow it as appropriate.
     */
    @Override
    public void write(Chunk<? extends GoodsFile> items) throws Exception {
        for (GoodsFile item : items) {
            log.info(String.format("keyword:%s,maker_code:%s,メーカー名:%s,商品コード:%s,商品名:%s", item.get商品名索引(), item.getメーカーコード(), item.getメーカー名(), item.get商品コード(), item.get商品名()));
            IGoodsFileWriterLogic goodsFileProcessorLogic = this.goodsFileWriterFactory.getGoodsFileProcessorLogic(item);
            goodsFileProcessorLogic.register();
        }
    }

}
