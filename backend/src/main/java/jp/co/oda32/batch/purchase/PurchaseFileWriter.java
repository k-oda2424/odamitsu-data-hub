package jp.co.oda32.batch.purchase;

import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.domain.service.smile.WSmilePurchaseOutputFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.Chunk;

import java.util.List;

/**
 * 仕入ファイル取り込みバッチWriterクラス
 *
 * @author k_oda
 * @since 2019/06/28
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class PurchaseFileWriter implements ItemWriter<ExtPurchaseFile> {

    @NonNull
    private WSmilePurchaseOutputFileService wSmilePurchaseOutputFileService;

    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * @param items items to be written
     */
    @Override
    public void write(Chunk<? extends ExtPurchaseFile> items) {
        for (ExtPurchaseFile item : items) {
            log.info(String.format("shop_no:%d 伝票日付:%s,伝票番号:%s,明細番号:%s,仕入先：%s,商品名：%s", item.getShopNo(), item.get伝票日付(), item.get伝票番号(), item.get行(), item.get仕入先名略称(), item.get商品名()));
            WSmilePurchaseOutputFile wSmilePurchaseOutputFile = WSmilePurchaseOutputFile.convertSmilePurchaseFile(item);
            wSmilePurchaseOutputFileService.save(wSmilePurchaseOutputFile);
        }
    }

}
